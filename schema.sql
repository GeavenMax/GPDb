-- GEVI Offline SQLite Schema with FTS5 Full-Text Search
-- Optimized for macOS / iOS / Android cross-platform offline retrieval

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;

-- 1. 影片表
CREATE TABLE IF NOT EXISTS movies (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    studio_id INTEGER,
    studio_name TEXT,
    release_year INTEGER,
    duration_mins INTEGER,
    category TEXT,
    rating TEXT,
    movie_type TEXT,
    description TEXT,
    cover_icon TEXT,
    cover_full TEXT,
    -- 封底图 (the "b" variant). '' = 已确认无封底, NULL = 未确认。
    -- 只在读到封面区 (coverContainer) 时才写入，所以 NULL 只有一个含义：还没确认过。
    -- 以后全量下载封面时直接选: cover_full <> '' AND cover_back IS NULL
    -- 即可拿到"还没确认封底"的影片，不必重抓已确认的。
    cover_back TEXT,
    -- 全部封面变体，按站点顺序: [0] 正面, [1] 封底(后缀 b), 之后 c/d/... 为更多变体。
    -- 只有该片确实有封面时才有值；一处封面都没有的影片页面不会渲染封面区，
    -- 此时保持 NULL，并由 scrape_voids 的 'cover' 记录"源头无封面"。
    covers_json TEXT,
    director_id INTEGER,
    director_name TEXT,
    description_zh TEXT,            -- 机器翻译后的中文剧情简介 (NULL = 尚未翻译)
    translation_attempts INTEGER DEFAULT 0,  -- 翻译失败重试计数，避免死循环
    scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_movies_year ON movies(release_year);
CREATE INDEX IF NOT EXISTS idx_movies_studio ON movies(studio_name);
CREATE INDEX IF NOT EXISTS idx_movies_category ON movies(category);
CREATE INDEX IF NOT EXISTS idx_movies_director ON movies(director_name);

-- 2. 演员档案表
CREATE TABLE IF NOT EXISTS performers (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    hair TEXT,
    eyes TEXT,
    body_hair TEXT,
    facial_hair TEXT,
    height TEXT,
    weight TEXT,
    build TEXT,
    skin TEXT,
    dick_size TEXT,
    foreskin TEXT,
    tattoos TEXT,
    notes TEXT,
    image_url TEXT,                 -- 演员头像 images/Stars/performer{N}.jpg (NULL = 该演员无照片)
    scraped_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_performers_name ON performers(name);
CREATE INDEX IF NOT EXISTS idx_performers_build ON performers(build);
CREATE INDEX IF NOT EXISTS idx_performers_hair ON performers(hair);
CREATE INDEX IF NOT EXISTS idx_performers_eyes ON performers(eyes);
CREATE INDEX IF NOT EXISTS idx_performers_skin ON performers(skin);
CREATE INDEX IF NOT EXISTS idx_performers_body_hair ON performers(body_hair);
CREATE INDEX IF NOT EXISTS idx_performers_facial_hair ON performers(facial_hair);
CREATE INDEX IF NOT EXISTS idx_performers_image ON performers(image_url);

-- 3. 影片-演员多对多关联表
CREATE TABLE IF NOT EXISTS movie_performers (
    movie_id INTEGER NOT NULL,
    performer_id INTEGER NOT NULL,
    performer_name TEXT,
    PRIMARY KEY (movie_id, performer_id),
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mp_performer_id ON movie_performers(performer_id);

-- 4. 场景片段表 (Episodes / Scenes)
CREATE TABLE IF NOT EXISTS episodes (
    id INTEGER PRIMARY KEY,
    movie_id INTEGER,
    title TEXT,
    thumbnail_url TEXT,
    description TEXT,
    action_notes TEXT,
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_episodes_movie_id ON episodes(movie_id);

-- 5. 爬取断点与进度跟踪表 (Checkpoint & Progress Tracking)
CREATE TABLE IF NOT EXISTS scrape_progress (
    item_type TEXT NOT NULL,        -- 'movie' 或 'performer'
    item_id INTEGER NOT NULL,
    status INTEGER NOT NULL,        -- 200: 抓取成功, 404: 页面不存在, 500: 错误
    last_scraped TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (item_type, item_id)
);

CREATE INDEX IF NOT EXISTS idx_progress_type_status ON scrape_progress(item_type, status);

-- 5b. 源头确实没有的字段 (Fields verified absent at the source)
--
-- Some records genuinely lack data: GEVI prints "?" where it has no release year,
-- and many films simply have no cover image. Without this table the scraper cannot
-- tell "the site has no year for this film" from "our parser failed to read the
-- year", so 缺字段 mode would re-fetch the same films forever and never improve
-- anything. One row means: we fetched this item and the field did not come back.
CREATE TABLE IF NOT EXISTS scrape_voids (
    item_type TEXT NOT NULL,        -- 'movie' 或 'performer'
    item_id INTEGER NOT NULL,
    field TEXT NOT NULL,            -- description / year / duration / cover / cast / ...
    attempts INTEGER NOT NULL DEFAULT 1,   -- 多少次抓取后该字段仍然为空
    noted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (item_type, item_id, field)
);

CREATE INDEX IF NOT EXISTS idx_voids_item ON scrape_voids(item_type, item_id);

-- 6. FTS5 全文检索引擎虚拟表
CREATE VIRTUAL TABLE IF NOT EXISTS movies_fts USING fts5(
    id UNINDEXED,
    title,
    studio_name,
    category,
    description,
    performers,
    tokenize = 'unicode61'
);

CREATE VIRTUAL TABLE IF NOT EXISTS performers_fts USING fts5(
    id UNINDEXED,
    name,
    tattoos,
    notes,
    tokenize = 'unicode61'
);

-- 7. 场景分集-演员多对多关联表 (Episode Performers)
CREATE TABLE IF NOT EXISTS episode_performers (
    episode_id INTEGER NOT NULL,
    performer_id INTEGER NOT NULL,
    performer_name TEXT,
    PRIMARY KEY (episode_id, performer_id),
    FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ep_performer ON episode_performers(performer_id);

-- 8. 用户自定义扩展系统 (Custom Tags & User Movie Meta)
CREATE TABLE IF NOT EXISTS user_tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    color TEXT DEFAULT '#f59e0b',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS movie_user_tags (
    movie_id INTEGER NOT NULL,
    tag_id INTEGER NOT NULL,
    PRIMARY KEY (movie_id, tag_id),
    FOREIGN KEY (tag_id) REFERENCES user_tags(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_movie_data (
    movie_id INTEGER PRIMARY KEY,
    rating REAL,                    -- 私密评星 0.5 ~ 5.0
    status TEXT,                    -- 'wishlist', 'watched', 'favorite'
    notes TEXT,                     -- 私密笔记与短评
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

