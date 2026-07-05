-- ============================================================
-- Run this ONCE to create the database before starting the app.
-- Execute via MariaDB/MySQL client:
--   mysql -u root -p < init-db.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS healthassist
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON healthassist.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
