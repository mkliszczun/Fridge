DELETE FROM planned_meal_reservation reservation
USING planned_meal_ingredient ingredient,
      planned_meal meal
WHERE reservation.planned_meal_ingredient_id = ingredient.id
  AND ingredient.planned_meal_id = meal.id
  AND meal.completed_at IS NOT NULL;
