CREATE TABLE account (
    account_id      VARCHAR(64) PRIMARY KEY,
    account_name    VARCHAR(128) NOT NULL UNIQUE,
    account_pwd     VARCHAR(255) NOT NULL,
    account_auth    VARCHAR(512) NOT NULL DEFAULT 'CREATE_NEW_FOLDER,UPLOAD_FILES,DELETE_FILE_OR_FOLDER,RENAME_FILE_OR_FOLDER,DOWNLOAD_FILES,MOVE_FILES',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE folder (
    folder_id             VARCHAR(64) PRIMARY KEY,
    folder_name           VARCHAR(255) NOT NULL,
    folder_creation_date  VARCHAR(32) NOT NULL,
    folder_creator        VARCHAR(128) NOT NULL,
    folder_parent         VARCHAR(64),
    folder_constraint     INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_folder_parent ON folder(folder_parent);

CREATE TABLE file_node (
    file_id              VARCHAR(64) PRIMARY KEY,
    file_name            VARCHAR(512) NOT NULL,
    file_size            VARCHAR(64) NOT NULL,
    file_parent_folder   VARCHAR(64) NOT NULL,
    file_creation_date   VARCHAR(32) NOT NULL,
    file_creator         VARCHAR(128) NOT NULL,
    file_path            VARCHAR(512) NOT NULL
);

CREATE INDEX idx_file_parent ON file_node(file_parent_folder);
CREATE INDEX idx_file_name ON file_node(file_name);

CREATE TABLE properties (
    propertie_key   VARCHAR(128) PRIMARY KEY,
    propertie_value TEXT
);

CREATE TABLE file_chain (
    chain_key    VARCHAR(128) PRIMARY KEY,
    file_id      VARCHAR(64) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at    TIMESTAMP
);

CREATE TABLE download_key (
    download_key VARCHAR(128) PRIMARY KEY,
    file_id      VARCHAR(64) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at    TIMESTAMP
);

INSERT INTO folder (folder_id, folder_name, folder_creation_date, folder_creator, folder_parent, folder_constraint)
VALUES ('root', 'ROOT', '2024-01-01 00:00:00', 'SYSTEM', NULL, 0);

INSERT INTO properties (propertie_key, propertie_value)
VALUES ('notice', '欢迎使用 kiftd 前后端分离版。'),
       ('notice_md5', 'init');
