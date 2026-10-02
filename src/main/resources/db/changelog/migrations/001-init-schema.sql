--liquibase formatted sql

--changeset otus:001-create-users
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    second_name   VARCHAR(100) NOT NULL,
    birthdate     DATE,
    biography     TEXT,
    city          VARCHAR(100),
    gender        VARCHAR(20) CHECK (gender IN ('male', 'female')),
    password_hash VARCHAR(100) NOT NULL
);

--changeset otus:002-create-tokens
CREATE TABLE tokens (
    token     UUID PRIMARY KEY,
    user_id   UUID NOT NULL REFERENCES users (id),
    issued_at TIMESTAMP NOT NULL DEFAULT now()
);
