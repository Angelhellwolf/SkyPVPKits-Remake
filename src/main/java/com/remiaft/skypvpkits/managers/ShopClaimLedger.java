package com.remiaft.skypvpkits.managers;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Reservation and conditional compensation; callers serialize access to their JDBC connection. */
public final class ShopClaimLedger {
    public enum Status { RESERVED, ALREADY_CLAIMED, NOT_OWNED, ERROR }
    public static final class Reservation {
        public final Status status;
        private final UUID player;
        private final String family, promotion;
        private final int level;
        private final LocalDateTime previous, claimed;
        private Reservation(Status status, UUID player, String family, int level, String promotion,
                            LocalDateTime previous, LocalDateTime claimed) {
            this.status = status; this.player = player; this.family = family; this.level = level;
            this.promotion = promotion; this.previous = previous; this.claimed = claimed;
        }
        private String table() { return promotion == null ? "player_kits" : "promotion_claims"; }
        private String key() { return "player_uuid = ? AND kit_type = ? AND kit_level = ?" + (promotion == null ? "" : " AND promotion_id = ?"); }
        private int bind(PreparedStatement sql, int offset) throws SQLException {
            sql.setString(offset++, player.toString()); sql.setString(offset++, family); sql.setInt(offset++, level);
            if (promotion != null) sql.setString(offset++, promotion);
            return offset;
        }
    }
    private ShopClaimLedger() { }
    public static Reservation error() { return new Reservation(Status.ERROR, null, null, 0, null, null, null); }

    public static Reservation reserve(Connection connection, UUID player, String family, int level,
                                      String promotion, LocalDateTime dayStart, LocalDateTime now) throws SQLException {
        LocalDateTime claimed = now.withNano(0);
        Reservation key = new Reservation(Status.RESERVED, player, family, level, promotion, null, claimed);
        LocalDateTime previous = null;
        boolean exists;
        try (PreparedStatement sql = connection.prepareStatement("SELECT last_claim_time FROM " + key.table() + " WHERE " + key.key())) {
            key.bind(sql, 1);
            try (ResultSet rows = sql.executeQuery()) {
                exists = rows.next();
                if (exists && rows.getTimestamp(1) != null) previous = rows.getTimestamp(1).toLocalDateTime();
            }
        }
        if (!exists && promotion == null) return new Reservation(Status.NOT_OWNED, player, family, level, null, null, claimed);
        if (previous != null && !previous.isBefore(dayStart))
            return new Reservation(Status.ALREADY_CLAIMED, player, family, level, promotion, previous, claimed);
        if (exists) {
            try (PreparedStatement sql = connection.prepareStatement("UPDATE " + key.table() + " SET last_claim_time = ? WHERE "
                    + key.key() + " AND (last_claim_time IS NULL OR last_claim_time < ?)")) {
                sql.setTimestamp(1, Timestamp.valueOf(claimed));
                sql.setTimestamp(key.bind(sql, 2), Timestamp.valueOf(dayStart));
                if (sql.executeUpdate() != 1) return new Reservation(Status.ALREADY_CLAIMED, player, family, level, promotion, previous, claimed);
            }
        } else {
            try (PreparedStatement sql = connection.prepareStatement("INSERT INTO promotion_claims "
                    + "(player_uuid, kit_type, kit_level, promotion_id, last_claim_time) VALUES (?, ?, ?, ?, ?)")) {
                sql.setTimestamp(key.bind(sql, 1), Timestamp.valueOf(claimed));
                if (sql.executeUpdate() != 1) return error();
            }
        }
        return new Reservation(Status.RESERVED, player, family, level, promotion, previous, claimed);
    }

    public static boolean restore(Connection connection, Reservation reservation) throws SQLException {
        if (reservation.status != Status.RESERVED) return false;
        boolean delete = reservation.promotion != null && reservation.previous == null;
        String query = delete ? "DELETE FROM " + reservation.table() + " WHERE " : "UPDATE " + reservation.table() + " SET last_claim_time = ? WHERE ";
        query += reservation.key() + " AND last_claim_time = ?";
        try (PreparedStatement sql = connection.prepareStatement(query)) {
            int offset = 1;
            if (!delete) {
                if (reservation.previous == null) sql.setNull(offset++, Types.TIMESTAMP);
                else sql.setTimestamp(offset++, Timestamp.valueOf(reservation.previous));
            }
            sql.setTimestamp(reservation.bind(sql, offset), Timestamp.valueOf(reservation.claimed));
            return sql.executeUpdate() == 1;
        }
    }
}
