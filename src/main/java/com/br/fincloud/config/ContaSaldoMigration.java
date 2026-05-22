package com.br.fincloud.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ContaSaldoMigration {

    private static final Logger log = LoggerFactory.getLogger(ContaSaldoMigration.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ContaSaldoMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrarSchemaContas() {
        atualizarCheckTipoConta();
        removerSaldoInicialLegado();
    }

    private void atualizarCheckTipoConta() {
        try {
            jdbcTemplate.execute("alter table contas drop constraint if exists contas_tipo_check");
            jdbcTemplate.execute("""
                    alter table contas
                    add constraint contas_tipo_check
                    check (tipo in (
                        'CONTA_CORRENTE',
                        'CONTA_POUPANCA',
                        'CARTEIRA',
                        'CARTAO_CREDITO',
                        'INVESTIMENTO',
                        'SALARIO',
                        'CAIXA'
                    ))
                    """);
            log.info("Constraint contas_tipo_check atualizado com os tipos de conta atuais");
        } catch (DataAccessException ex) {
            log.warn("Nao foi possivel atualizar constraint contas_tipo_check", ex);
        }
    }

    private void removerSaldoInicialLegado() {
        try {
            if (!hasColumn("contas", "saldo_inicial")) {
                return;
            }

            if (hasColumn("contas", "saldo_atual")) {
                int atualizadas = jdbcTemplate.update("""
                        update contas
                        set saldo_atual = saldo_inicial
                        where saldo_inicial is not null
                          and saldo_inicial <> 0
                          and (saldo_atual is null or saldo_atual = 0)
                        """);

                if (atualizadas > 0) {
                    log.info("Saldos atuais migrados de saldo_inicial para saldo_atual: {}", atualizadas);
                }
            }

            jdbcTemplate.execute("alter table contas drop column saldo_inicial");
            log.info("Coluna legada saldo_inicial removida da tabela contas");
        } catch (SQLException ex) {
            log.warn("Nao foi possivel verificar colunas legadas de saldo das contas", ex);
        } catch (DataAccessException ex) {
            log.warn("Nao foi possivel remover coluna legada saldo_inicial", ex);
        }
    }

    private boolean hasColumn(String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return hasColumn(connection, tableName, columnName)
                    || hasColumn(connection, tableName.toUpperCase(), columnName.toUpperCase());
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
