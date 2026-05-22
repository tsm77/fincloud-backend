package com.br.fincloud.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    public void migrarSaldoAtual() {
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
