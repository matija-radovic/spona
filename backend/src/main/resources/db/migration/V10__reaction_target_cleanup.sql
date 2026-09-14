CREATE FUNCTION delete_target_reactions() RETURNS trigger
  LANGUAGE plpgsql AS $$
BEGIN
  DELETE FROM reaction r
   USING reaction_target_types t
   WHERE t.table_name = TG_ARGV[0]
     AND r.target_type_id = t.target_type_id
     AND r.target_id = OLD.id;
  RETURN NULL;
END
$$;

CREATE TRIGGER post_reaction_cleanup
  AFTER DELETE ON post
  FOR EACH ROW EXECUTE FUNCTION delete_target_reactions('posts');

CREATE TRIGGER comment_reaction_cleanup
  AFTER DELETE ON comment
  FOR EACH ROW EXECUTE FUNCTION delete_target_reactions('comments');

DELETE FROM reaction r
 USING reaction_target_types t
 WHERE r.target_type_id = t.target_type_id
   AND ((t.table_name = 'posts'    AND NOT EXISTS (SELECT 1 FROM post p    WHERE p.id = r.target_id))
     OR (t.table_name = 'comments' AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.id = r.target_id)));
