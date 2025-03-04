CREATE OR REPLACE FUNCTION calculate_article_rating(p_article_id BIGINT)
    RETURNS FLOAT AS
$$
DECLARE
    likes    INT;
    dislikes INT;
BEGIN
    SELECT COUNT(*)
    INTO likes
    FROM user_reactions
    WHERE article_id = p_article_id
      AND reaction_type = 'LIKE';

    SELECT COUNT(*)
    INTO dislikes
    FROM user_reactions
    WHERE article_id = p_article_id
      AND reaction_type = 'DISLIKE';

    RETURN CASE
               WHEN (likes + dislikes) = 0 THEN 0.0
               ELSE (likes::FLOAT - dislikes::FLOAT) / (likes + dislikes)::FLOAT
        END;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_article_reaction()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO article_reactions (article_id, likes_count, dislikes_count, rating)
    VALUES (NEW.article_id, 0, 0, 0.0)
    ON CONFLICT (article_id) DO NOTHING;

    UPDATE article_reactions
    SET likes_count    = (SELECT COUNT(*)
                          FROM user_reactions
                          WHERE article_id = NEW.article_id
                            AND reaction_type = 'LIKE'),
        dislikes_count = (SELECT COUNT(*)
                          FROM user_reactions
                          WHERE article_id = NEW.article_id
                            AND reaction_type = 'DISLIKE'),
        rating         = calculate_article_rating(NEW.article_id)
    WHERE article_id = NEW.article_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS user_reaction_trigger ON user_reactions;
CREATE TRIGGER user_reaction_trigger
    AFTER INSERT OR UPDATE OR DELETE
    ON user_reactions
    FOR EACH ROW
EXECUTE FUNCTION update_article_reaction();