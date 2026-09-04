ALTER TABLE planned_meal
    ADD COLUMN completed_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX idx_planned_meal_fridge_completed_date
    ON planned_meal (fridge_id, completed_at, planned_date);
