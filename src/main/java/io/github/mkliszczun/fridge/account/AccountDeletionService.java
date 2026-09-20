package io.github.mkliszczun.fridge.account;

import io.github.mkliszczun.fridge.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AccountDeletionService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public AccountDeletionService(UserRepository users, PasswordEncoder passwords, JdbcTemplate jdbc,
                                  EntityManager entityManager) {
        this.users = users;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Transactional
    public void delete(UUID userId, String password) {
        var user = users.findLockedById(userId).orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        entityManager.refresh(user);
        if (!passwords.matches(password, user.getPassword())) throw new BadCredentialsException("Incorrect password");
        var fridgeIds = jdbc.queryForList("select fridge_id from fridge_member where user_id = ? order by fridge_id", UUID.class, userId);
        for (UUID fridgeId : fridgeIds) {
            jdbc.queryForList("select id from fridge where id = ? for update", UUID.class, fridgeId);
            var remaining = jdbc.queryForList("select user_id from fridge_member where fridge_id = ? and user_id <> ? order by id",
                    UUID.class, fridgeId, userId);
            if (remaining.isEmpty()) {
                deleteMeals("fridge_id", fridgeId);
                jdbc.update("delete from shopping_list_item_source where shopping_list_item_id in (select id from shopping_list_item where fridge_id = ?)", fridgeId);
                jdbc.update("delete from shopping_list_item where fridge_id = ?", fridgeId);
                jdbc.update("delete from planned_meal_reservation where fridge_item_id in (select id from fridge_item where fridge_id = ?)", fridgeId);
                jdbc.update("delete from fridge_item where fridge_id = ?", fridgeId);
                jdbc.update("delete from fridge_member where fridge_id = ?", fridgeId);
                jdbc.update("delete from fridge where id = ?", fridgeId);
            } else {
                Long owners = jdbc.queryForObject("select count(*) from fridge_member where fridge_id = ? and user_id <> ? and role_in_fridge = 'OWNER'",
                        Long.class, fridgeId, userId);
                if (owners != null && owners == 0) {
                    jdbc.update("update fridge_member set role_in_fridge = 'OWNER' where fridge_id = ? and user_id = ?", fridgeId, remaining.get(0));
                }
            }
        }
        deleteMeals("created_by_user_id", userId);
        jdbc.update("update planned_meal set source_recipe_id = null where source_recipe_id in (select id from recipe where owner_user_id = ?)", userId);
        jdbc.update("delete from recipe_ingredient where recipe_id in (select id from recipe where owner_user_id = ?)", userId);
        jdbc.update("delete from recipe where owner_user_id = ?", userId);
        jdbc.update("update fridge_item set owner_user_id = null where owner_user_id = ?", userId);
        jdbc.update("delete from fridge_member where user_id = ?", userId);
        jdbc.update("delete from refresh_token where user_id = ?", userId);
        jdbc.update("delete from email_verification where user_id = ?", userId);
        jdbc.update("delete from ai_daily_usage where user_id = ?", userId);
        jdbc.update("delete from user_roles where user_id = ?", userId);
        jdbc.update("delete from users where id = ?", userId);
        entityManager.clear();
    }

    private void deleteMeals(String column, UUID id) {
        // column is an internal constant, never request input. Explicit order also supports Hibernate test schemas.
        String meals = "select id from planned_meal where " + column + " = ?";
        String ingredients = "select id from planned_meal_ingredient where planned_meal_id in (" + meals + ")";
        jdbc.update("delete from shopping_list_item_source where planned_meal_ingredient_id in (" + ingredients + ")", id);
        jdbc.update("delete from planned_meal_reservation where planned_meal_ingredient_id in (" + ingredients + ")", id);
        jdbc.update("delete from planned_meal_ingredient where planned_meal_id in (" + meals + ")", id);
        jdbc.update("delete from planned_meal where " + column + " = ?", id);
    }
}
