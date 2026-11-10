package com.remiaft.skypvpkits.managers;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ShopClaimLedgerTest {
    Connection db;
    final UUID player = UUID.randomUUID();
    final LocalDateTime now = LocalDateTime.of(2026, 9, 4, 22, 0);
    final LocalDateTime start = now.toLocalDate().atTime(4, 0);
    @BeforeEach void open() throws Exception {
        db = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement sql = db.createStatement()) {
            sql.execute("CREATE TABLE player_kits(player_uuid TEXT,kit_type TEXT,kit_level INTEGER,last_claim_time TIMESTAMP,PRIMARY KEY(player_uuid,kit_type,kit_level))");
            sql.execute("CREATE TABLE promotion_claims(player_uuid TEXT,kit_type TEXT,kit_level INTEGER,promotion_id TEXT,last_claim_time TIMESTAMP NOT NULL,PRIMARY KEY(player_uuid,promotion_id,kit_type,kit_level))");
        }
    }
    @AfterEach void close() throws Exception { db.close(); }
    ShopClaimLedger.Reservation reserve(String promotion) throws Exception {
        return ShopClaimLedger.reserve(db, player, "mars", 4, promotion, start, now);
    }
    void owned(LocalDateTime previous) throws Exception {
        try (PreparedStatement sql = db.prepareStatement("INSERT INTO player_kits VALUES (?,?,?,?)")) {
            sql.setString(1, player.toString()); sql.setString(2, "mars"); sql.setInt(3, 4);
            if (previous == null) sql.setNull(4, Types.TIMESTAMP); else sql.setTimestamp(4, Timestamp.valueOf(previous));
            sql.executeUpdate();
        }
    }
    @Test void ownedClaimHasOneWinnerAndCanRestoreUnclaimedState() throws Exception {
        owned(null);
        ShopClaimLedger.Reservation first = reserve(null);
        assertEquals(ShopClaimLedger.Status.RESERVED, first.status);
        assertEquals(ShopClaimLedger.Status.ALREADY_CLAIMED, reserve(null).status);
        assertTrue(ShopClaimLedger.restore(db, first));
        assertEquals(ShopClaimLedger.Status.RESERVED, reserve(null).status);
    }
    @Test void absentOwnershipCannotBeCreatedByClaim() throws Exception {
        assertEquals(ShopClaimLedger.Status.NOT_OWNED, reserve(null).status);
    }
    @Test void promotionDoesNotGrantPaidOwnershipAndRestoresMissingRow() throws Exception {
        ShopClaimLedger.Reservation first = reserve("event");
        assertEquals(ShopClaimLedger.Status.RESERVED, first.status);
        assertEquals(ShopClaimLedger.Status.NOT_OWNED, reserve(null).status);
        assertEquals(ShopClaimLedger.Status.ALREADY_CLAIMED, reserve("event").status);
        assertTrue(ShopClaimLedger.restore(db, first));
        assertFalse(ShopClaimLedger.restore(db, first));
        assertEquals(ShopClaimLedger.Status.RESERVED, reserve("event").status);
    }
    @Test void compensationRestoresPreviousDayExactly() throws Exception {
        LocalDateTime previous = now.minusDays(1);
        owned(previous);
        ShopClaimLedger.Reservation first = reserve(null);
        assertTrue(ShopClaimLedger.restore(db, first));
        try (Statement sql = db.createStatement(); ResultSet rows = sql.executeQuery("SELECT last_claim_time FROM player_kits")) {
            assertTrue(rows.next()); assertEquals(previous, rows.getTimestamp(1).toLocalDateTime());
        }
    }
    @Test void compensationCannotOverwriteALaterClaim() throws Exception {
        ShopClaimLedger.Reservation first = reserve("event");
        try (PreparedStatement sql = db.prepareStatement("UPDATE promotion_claims SET last_claim_time = ?")) {
            sql.setTimestamp(1, Timestamp.valueOf(now.plusSeconds(2))); sql.executeUpdate();
        }
        assertFalse(ShopClaimLedger.restore(db, first));
    }
    @Test void differentPromotionAndDayRemainIndependent() throws Exception {
        assertEquals(ShopClaimLedger.Status.RESERVED, reserve("one").status);
        assertEquals(ShopClaimLedger.Status.RESERVED, reserve("two").status);
        ShopClaimLedger.Reservation next = ShopClaimLedger.reserve(db, player, "mars", 4, "one", start.plusDays(1), now.plusDays(1));
        assertEquals(ShopClaimLedger.Status.RESERVED, next.status);
        assertTrue(ShopClaimLedger.restore(db, next));
        assertEquals(ShopClaimLedger.Status.ALREADY_CLAIMED, reserve("one").status);
    }
}
