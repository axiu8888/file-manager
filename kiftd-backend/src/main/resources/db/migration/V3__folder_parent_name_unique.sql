-- Merge duplicate (folder_parent, folder_name) rows left by concurrent creates,
-- then enforce uniqueness so createFolder can rely on get-or-create + DIVE retry.

WITH ranked AS (
    SELECT folder_id,
           FIRST_VALUE(folder_id) OVER (
               PARTITION BY folder_parent, folder_name
               ORDER BY folder_creation_date, folder_id
           ) AS keep_id
    FROM folder
),
dupes AS (
    SELECT folder_id, keep_id
    FROM ranked
    WHERE folder_id <> keep_id
)
UPDATE file_node fn
SET file_parent_folder = d.keep_id
FROM dupes d
WHERE fn.file_parent_folder = d.folder_id;

WITH ranked AS (
    SELECT folder_id,
           FIRST_VALUE(folder_id) OVER (
               PARTITION BY folder_parent, folder_name
               ORDER BY folder_creation_date, folder_id
           ) AS keep_id
    FROM folder
),
dupes AS (
    SELECT folder_id, keep_id
    FROM ranked
    WHERE folder_id <> keep_id
)
UPDATE folder f
SET folder_parent = d.keep_id
FROM dupes d
WHERE f.folder_parent = d.folder_id;

WITH ranked AS (
    SELECT folder_id,
           FIRST_VALUE(folder_id) OVER (
               PARTITION BY folder_parent, folder_name
               ORDER BY folder_creation_date, folder_id
           ) AS keep_id
    FROM folder
),
dupes AS (
    SELECT folder_id, keep_id
    FROM ranked
    WHERE folder_id <> keep_id
)
DELETE FROM folder f
USING dupes d
WHERE f.folder_id = d.folder_id;

-- NULL parent (root only) is fine: PG unique treats NULLs as distinct.
CREATE UNIQUE INDEX IF NOT EXISTS uk_folder_parent_name
    ON folder (folder_parent, folder_name);
