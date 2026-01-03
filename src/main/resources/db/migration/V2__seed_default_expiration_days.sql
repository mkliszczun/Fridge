INSERT INTO default_expiration_days (product_type, default_expiration_days, expiration_days_after_opening)
VALUES
  ('DAIRY',      14, 3),
  ('MEAT',        5, 2),
  ('FISH',        3, 1),
  ('VEGETABLE',   7, 3),
  ('FRUIT',       7, 3),
  ('BAKERY',      3, 2),
  ('DRY',       365, 100),
  ('BEVERAGE',  180, 5),
  ('OTHER',      30, 5)
ON CONFLICT (product_type) DO NOTHING;
