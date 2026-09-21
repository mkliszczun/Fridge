package io.github.mkliszczun.fridge.controller;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test
    void returnsUpAndClosesConnection() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        var result = new HealthController(source).health();
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).containsExactlyEntriesOf(java.util.Map.of("status", "UP"));
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(connection).close();
    }

    @Test
    void invalidConnectionReturns503AndIsClosed() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var result = new HealthController(source).health();
        assertThat(result.getStatusCode().value()).isEqualTo(503);
        assertThat(result.getBody()).containsExactlyEntriesOf(java.util.Map.of("status", "DOWN"));
        verify(connection).close();
    }

    @Test
    void databaseFailureDoesNotExposeConnectionDetails() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("jdbc:private-host password=secret", "08001"));
        var result = new HealthController(source).health();
        assertThat(result.getStatusCode().value()).isEqualTo(503);
        assertThat(result.getBody()).containsExactlyEntriesOf(java.util.Map.of("status", "DOWN"));
    }
}
