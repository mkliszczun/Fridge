ALTER TABLE shopping_list_item ADD COLUMN has_manual_entry BOOLEAN NOT NULL DEFAULT FALSE;
-- Old unquantified merged entries are ambiguous: preserve them rather than delete a user's manual entry.
UPDATE shopping_list_item i SET has_manual_entry = TRUE
WHERE manual_amount IS NOT NULL OR is_quantified = FALSE
   OR NOT EXISTS (SELECT 1 FROM shopping_list_item_source s WHERE s.shopping_list_item_id = i.id);
