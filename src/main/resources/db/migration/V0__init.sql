CREATE SEQUENCE IF NOT EXISTS website_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS category_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS article_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS article_reaction_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS user_reaction_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS notification_schedule_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS users
(
    user_id
    BIGINT
    PRIMARY
    KEY,
    last_submitted_article_id
    BIGINT
);

CREATE TABLE IF NOT EXISTS websites
(
    website_id
    BIGINT
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'website_id_seq'
),
    name       VARCHAR(255) UNIQUE NOT NULL,
    url        VARCHAR(255) UNIQUE NOT NULL,
    owner_id   BIGINT,
    CONSTRAINT fk_website_owner FOREIGN KEY (owner_id) REFERENCES users (user_id)
    );

CREATE TABLE IF NOT EXISTS categories
(
    category_id
    BIGINT
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'category_id_seq'
),
    name        VARCHAR(255) UNIQUE NOT NULL,
    owner_id    BIGINT,
    CONSTRAINT fk_category_owner FOREIGN KEY (owner_id) REFERENCES users (user_id)
    );

CREATE TABLE IF NOT EXISTS user_categories
(
    user_id
    BIGINT,
    category_id
    BIGINT,
    PRIMARY
    KEY
(
    user_id,
    category_id
),
    CONSTRAINT fk_user_category_user FOREIGN KEY
(
    user_id
) REFERENCES users
(
    user_id
) ON DELETE CASCADE,
    CONSTRAINT fk_user_category_category FOREIGN KEY
(
    category_id
) REFERENCES categories
(
    category_id
)
  ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS user_websites
(
    user_id
    BIGINT,
    website_id
    BIGINT,
    PRIMARY
    KEY
(
    user_id,
    website_id
),
    CONSTRAINT fk_user_website_user FOREIGN KEY
(
    user_id
) REFERENCES users
(
    user_id
) ON DELETE CASCADE,
    CONSTRAINT fk_user_website_website FOREIGN KEY
(
    website_id
) REFERENCES websites
(
    website_id
)
  ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS articles
(
    article_id
    BIGINT
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'article_id_seq'
),
    name        VARCHAR(255)        NOT NULL,
    description TEXT NOT NULL,
    site_date   VARCHAR(255)        NOT NULL,
    date        TIMESTAMP           NOT NULL,
    url         VARCHAR(255) UNIQUE NOT NULL,
    website_id  BIGINT              NOT NULL,
    CONSTRAINT fk_article_website FOREIGN KEY
(
    website_id
) REFERENCES websites
(
    website_id
) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS article_category
(
    article_id
    BIGINT
    NOT
    NULL,
    category_id
    BIGINT
    NOT
    NULL,
    PRIMARY
    KEY
(
    article_id,
    category_id
),
    FOREIGN KEY
(
    article_id
) REFERENCES articles
(
    article_id
) ON DELETE CASCADE,
    FOREIGN KEY
(
    category_id
) REFERENCES categories
(
    category_id
)
  ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS notification_schedules
(
    schedule_id
    BIGINT
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'notification_schedule_id_seq'
),
    user_id BIGINT NOT NULL,
    start_hour INTEGER NOT NULL,
    end_hour INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL,
    CONSTRAINT fk_schedule_user FOREIGN KEY
(
    user_id
) REFERENCES users
(
    user_id
) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS article_reactions
(
    article_reaction_id
    BIGINT
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'article_reaction_id_seq'
),
    article_id          BIGINT NOT NULL,
    likes_count INTEGER DEFAULT 0,
    dislikes_count INTEGER DEFAULT 0,
    rating FLOAT DEFAULT 0.0,
    CONSTRAINT fk_article_reaction_article FOREIGN KEY
(
    article_id
) REFERENCES articles
(
    article_id
) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_reactions
(
    user_reaction_id BIGINT PRIMARY KEY DEFAULT nextval('user_reaction_id_seq'),
    user_id          BIGINT      NOT NULL,
    article_id       BIGINT      NOT NULL,
    reaction_type    VARCHAR(10) NOT NULL CHECK (reaction_type IN ('LIKE', 'DISLIKE')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_reaction_user FOREIGN KEY
(
    user_id
) REFERENCES users
(
    user_id
) ON DELETE CASCADE,
    CONSTRAINT fk_user_reaction_article FOREIGN KEY
(
    article_id
) REFERENCES articles
(
    article_id
)
  ON DELETE CASCADE,
    CONSTRAINT unique_user_article_reaction UNIQUE (user_id, article_id)
);
