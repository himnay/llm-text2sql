package com.org.llm.service;

import com.org.llm.config.Text2SqlProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryExecutionServiceTest {

    private static final String SQL = "SELECT * FROM orders";

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final QueryExecutionService service = new QueryExecutionService(dataSource,
            new Text2SqlProperties("text2sql", 100, 1000, 30, "PROFILE", "openai", "model", ""));

    @ParameterizedTest(name = "requested {0} -> statement capped at {1}")
    @DisplayName("Row cap is clamped and applied to the statement that runs the query")
    @CsvSource(nullValues = "null", value = {"7, 7", "5000, 1000", "null, 100"})
    void appliesRowCapToTheStatement(Integer requested, int expectedMaxRows) throws SQLException {
        PreparedStatement statement = emptyResultStatement();

        service.execute(SQL, requested);

        verify(statement).setMaxRows(expectedMaxRows);
    }

    @Test
    @DisplayName("Each call caps only its own statement, so concurrent requests cannot swap caps")
    void eachCallCapsItsOwnStatement() throws SQLException {
        PreparedStatement first = emptyResultStatement();
        service.execute(SQL, 5);
        PreparedStatement second = emptyResultStatement();
        service.execute(SQL, 500);

        verify(first).setMaxRows(5);
        verify(second).setMaxRows(500);
    }

    private PreparedStatement emptyResultStatement() throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        return statement;
    }
}
