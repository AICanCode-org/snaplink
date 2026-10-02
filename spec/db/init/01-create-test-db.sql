-- Runs once when the Postgres container volume is first created.
-- Integration tests use a separate database so they never wipe your dev data.
CREATE DATABASE snaplink_test OWNER snaplink;
