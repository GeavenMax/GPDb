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
    translation_attempts INTEGER DEFAULT 0,  -- 简介翻译失败重试计数，避免死循环
    -- 机器翻译后的中文片名 (NULL = 尚未翻译)。译文优先体现原标题的双关/谐音。
    title_zh TEXT,
    -- 片名翻译失败重试计数。必须与 translation_attempts 分开：那个计数器是
    -- get_untranslated_movies 判断"这部片的简介还值不值得再译"的依据，两种翻译
    -- 合用同一个计数器的话，片名译失败 3 次就会把这部片永久踢出简介队列。
    title_attempts INTEGER DEFAULT 0,
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
    description_zh TEXT,            -- 机器翻译后的中文片段简介 (NULL = 尚未翻译)
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

-- 9. 收藏 (Favorites) —— 跨条目类型
--
-- entity_key 用 TEXT 而不是 INTEGER：影片/演员/片段确实有数字 ID，但片商和导演
-- 在本库里没有独立的表（只有 movies.studio_name / director_name 两列冗余字段）。
-- 影片库的片商筛选本来就是按名字做的（filters.studio = 名字），所以收藏键直接用
-- 名字，收藏 → 筛选这条链路中间不需要任何 ID 转换。
-- 约定: movie/performer/episode 存数字 ID 的字符串形式；studio/director 存名字本身。
CREATE TABLE IF NOT EXISTS user_favorites (
    entity_type TEXT NOT NULL,      -- 'movie' | 'performer' | 'studio' | 'director' | 'episode'
    entity_key TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_type, entity_key)
);

CREATE INDEX IF NOT EXISTS idx_user_fav_type ON user_favorites(entity_type, created_at DESC);

-- 10. 演员属性术语表 (Attribute glossary)
--
-- 演员档案里的属性值全部是英文，且词汇量极小 —— 8 个可筛选维度加上纹身部位，
-- 原子词一共只有约 73 个。与其每次展示都调翻译 API，不如统计出去重后的词条
-- 一次性翻译好存这里，之后十万个演员都从这里查表复用，不再产生任何 API 费用。
-- 因此这张表是"英文原文 → 中文"的被动查询表，不参与任何筛选逻辑：
-- 筛选仍然匹配英文值，这里只负责显示。
CREATE TABLE IF NOT EXISTS attr_glossary (
    en TEXT PRIMARY KEY,
    zh TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- 11. 导演 (Directors) —— 实体表 + 影片关联表
--
-- 背景：导演在站点上是独立实体（每个名字都指向 director/<ID>），但本库一直只在
-- movies 上留了 director_id / director_name 两列冗余字段。而解析阶段把
-- "Director:" 单元格里的 <a> 标签整段剥掉再拼接，于是多导演影片存成了一条人名
-- 首尾相接的粘连串 —— 库内最长 211 字符、18 个导演挤在一个"名字"里：
--
--   Fred HalstedPeter de RomeRobert PrionJim West...Jack Deveau
--
-- 后果是按导演检索永远匹配不上（筛选是精确等值 m.director_name = ?），
-- 而且 director_id 只留下了第一个导演的 ID（原正则的 group(1)）。
--
-- 这两张表把导演还原成实体：
--   directors        一个导演一行
--   movie_directors  影片 ↔ 导演，position 保留站点给出的顺序
--
-- 关于 id：站点 ID（director/242）只在重抓过该影片之后才知道，而库里这 33,757
-- 条历史数据只有名字。所以 id 用本库自增主键、site_id 单独可空存放站点 ID ——
-- 名字是唯一贯穿新老数据的键（收藏功能也是按名字存的，见 user_favorites）。
--
-- 建表不改 movies，所以分词结果可以随时重跑：
--   DELETE FROM movie_directors;  然后重跑 --mode directors --apply 即可回到空状态。
CREATE TABLE IF NOT EXISTS directors (
    id INTEGER PRIMARY KEY,          -- 本库内部 ID（rowid 自增）
    site_id INTEGER UNIQUE,          -- 站点 director/<ID>，重抓后回填；分词阶段为 NULL
    name TEXT NOT NULL UNIQUE,
    works_count INTEGER DEFAULT 0,   -- 影片数，由 --mode directors 回填
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS movie_directors (
    movie_id INTEGER NOT NULL,
    director_id INTEGER NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,   -- 站点顺序，从 0 开始；分词结果里是串内顺序
    PRIMARY KEY (movie_id, director_id)
);

CREATE INDEX IF NOT EXISTS idx_movie_directors_director ON movie_directors(director_id);
CREATE INDEX IF NOT EXISTS idx_directors_name ON directors(name);


-- 12. 影片分类术语表 (Category glossary)
--
-- 与第 10 节的 attr_glossary 是同一个思路（一次性翻译、之后查表复用、不参与筛选），
-- 但刻意分成两张表而不是合并：
--
--   * 词源、prompt、收集器、生命周期都不同 —— 属性词来自 performers 的 8 个维度，
--     分类词来自 movies.category，两者的翻译口径完全不一样；
--   * 合并后前端的 tr() 会变得有歧义。Muscle、Twink、Bareback 这类词今天恰好
--     只出现在一边（实测 53 个分类词与 80 个属性词零交集），但那是运气不是设计。
--
-- 存的是**原子词**，不是 movies.category 的原始值。该列有 55 行是用 <br /> 连接的
-- 多值串（如 'Wrestling<br />J/O'），去重后共 74 个原始值，拆开只有 53 个词；
-- 按原始值建表的话，每出现一个新组合都要多一条记录。拆分用 server.split_facet_value，
-- 显示时再逐词查表拼回去。
--
-- 与 attr_glossary 一样，筛选逻辑仍然匹配英文值，这张表只负责显示。
CREATE TABLE IF NOT EXISTS category_glossary (
    term       TEXT PRIMARY KEY,   -- 原子词，已按 <br /> 拆开，如 'J/O'
    zh         TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
