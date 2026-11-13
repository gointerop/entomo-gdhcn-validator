package co.entomo.gdhcn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import co.entomo.gdhcn.entity.IpsFile;
import co.entomo.gdhcn.entity.QrCode;
import co.entomo.gdhcn.repository.IpsFileRepository;
import co.entomo.gdhcn.repository.QrCodeRepository;

/**
 * Runs against a real PostgreSQL (skipped automatically when Docker is unavailable):
 * Flyway builds the schema from scratch and Hibernate validates the entities against it,
 * and a database created by the old ddl-auto=update is baselined without being touched.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTests {

	static final String IMAGE = "postgres:18-alpine";

	/** Same DDL the application created through ddl-auto=update before Flyway was introduced. */
	static final String LEGACY_DDL = """
			create table ips_file (is_accessed boolean default false, created_at timestamp(6) not null,
			  id varchar(255) not null, manifest_id varchar(255) not null, primary key (id));
			create table qr_code (created_at timestamp(6) not null, expires_on timestamp(6), id varchar(36) not null,
			  country_code varchar(255), flag varchar(255), json_url varchar(255), key varchar(255),
			  manifest_id varchar(255) not null, pass_code varchar(255), primary key (id));
			""";

	@Nested
	@SpringBootTest
	@ActiveProfiles({ "pgtest", "fileSystem" })
	class FreshDatabase {

		@Container
		@ServiceConnection
		static PostgreSQLContainer postgres = new PostgreSQLContainer(IMAGE);

		@MockitoBean
		HttpClient httpClient;

		@Autowired
		JdbcTemplate jdbc;
		@Autowired
		QrCodeRepository qrCodes;
		@Autowired
		IpsFileRepository ipsFiles;

		@Test
		void flywayAppliedTheBaselineAndHibernateValidatedIt() {
			List<Map<String, Object>> history = jdbc.queryForList(
					"select version, success from flyway_schema_history where version is not null order by installed_rank");
			assertEquals(List.of("1", "2"), history.stream().map(h -> h.get("version")).toList());
			history.forEach(h -> assertEquals(true, h.get("success")));
		}

		@Test
		void manifestIdIndexesExistAndAreValid() {
			List<String> valid = jdbc.queryForList("""
					select c.relname from pg_index i join pg_class c on c.oid = i.indexrelid
					where i.indisvalid and c.relname in ('idx_qr_code_manifest_id', 'idx_ips_file_manifest_id')
					order by c.relname
					""", String.class);
			assertEquals(List.of("idx_ips_file_manifest_id", "idx_qr_code_manifest_id"), valid);
		}

		@Test
		void timestampColumnsStayWithoutTimeZone() {
			List<String> types = jdbc.queryForList("""
					select data_type from information_schema.columns
					where table_name in ('qr_code','ips_file') and column_name in ('created_at','expires_on')
					""", String.class);
			assertEquals(3, types.size());
			types.forEach(t -> assertEquals("timestamp without time zone", t));
		}

		@Test
		void instantsRoundTripAndTimestampsAreGenerated() {
			Instant expires = Instant.parse("2099-01-01T00:00:00.123Z");
			QrCode qr = new QrCode();
			qr.setId(UUID.randomUUID().toString());
			qr.setManifestId("m-" + UUID.randomUUID());
			qr.setExpiresOn(expires);
			qrCodes.saveAndFlush(qr);

			QrCode loaded = qrCodes.findByManifestId(qr.getManifestId()).orElseThrow();
			assertEquals(expires.truncatedTo(ChronoUnit.MICROS), loaded.getExpiresOn());
			assertNotNull(loaded.getCreatedAt());
			assertTrue(Math.abs(Instant.now().toEpochMilli() - loaded.getCreatedAt().toEpochMilli()) < 60_000,
					"created_at should be 'now' in UTC, was " + loaded.getCreatedAt());

			IpsFile ips = new IpsFile();
			ips.setManifestId(qr.getManifestId());
			IpsFile saved = ipsFiles.saveAndFlush(ips);
			assertEquals(36, saved.getId().length(), "UUID primary key");
			assertNotNull(ipsFiles.findById(saved.getId()).orElseThrow().getCreatedAt());
		}
	}

	@Nested
	class LegacyDatabase {

		@Container
		static PostgreSQLContainer postgres = new PostgreSQLContainer(IMAGE);

		@Test
		void existingSchemaIsBaselinedAndItsDataSurvives() throws Exception {
			try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
					postgres.getPassword()); Statement st = c.createStatement()) {
				st.execute(LEGACY_DDL);
				st.execute("insert into qr_code (created_at, id, manifest_id) values (now(), 'legacy-1', 'legacy-m')");
			}

			Flyway flyway = Flyway.configure()
					.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
					.baselineOnMigrate(true).baselineVersion("1")
					.configuration(Map.of("flyway.postgresql.transactional.lock", "false")).load();
			var result = flyway.migrate();
			assertTrue(result.success);
			assertEquals(1, result.migrationsExecuted, "only V2 runs; the baseline must not re-run on an existing schema");

			try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
					postgres.getPassword()); Statement st = c.createStatement()) {
				ResultSet rs = st.executeQuery("select count(*) from qr_code where id = 'legacy-1'");
				rs.next();
				assertEquals(1, rs.getInt(1));
				rs = st.executeQuery("select version, type from flyway_schema_history order by installed_rank");
				rs.next();
				assertEquals("1", rs.getString("version"));
				assertEquals("BASELINE", rs.getString("type"));
				rs.next();
				assertEquals("2", rs.getString("version"));
				assertEquals("SQL", rs.getString("type"));
				rs = st.executeQuery("""
						select count(*) from pg_indexes
						where indexname in ('idx_qr_code_manifest_id', 'idx_ips_file_manifest_id')""");
				rs.next();
				assertEquals(2, rs.getInt(1));
			}
		}

		@Test
		void baselineScriptMatchesTheLegacyDdl() throws Exception {
			// guards against the checked-in V1 drifting from what ddl-auto=update used to create
			String v1 = Files.readString(Path.of("src/main/resources/db/migration/V1__baseline.sql"),
					StandardCharsets.UTF_8);
			String norm = v1.lines().filter(l -> !l.startsWith("--")).reduce("", (a, b) -> a + " " + b)
					.replaceAll("\\s+", " ").replace("( ", "(").replace(" )", ")").replace(" ;", ";").trim();
			String legacy = LEGACY_DDL.replaceAll("\\s+", " ").replace("( ", "(").replace(" )", ")")
					.replace(" ;", ";").trim();
			assertEquals(legacy, norm);
		}
	}
}
