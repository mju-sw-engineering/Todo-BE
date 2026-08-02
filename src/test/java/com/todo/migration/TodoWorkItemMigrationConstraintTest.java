package com.todo.migration;

import com.todo.support.MySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2는 generated column과 복합 FK의 동작이 MySQL과 달라 실제 Flyway 스키마에서 검증한다.
 */
@SpringBootTest
@Transactional
class TodoWorkItemMigrationConstraintTest extends MySqlTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void DIRECT_중복_배정과_mode가_다른_WorkItem은_MySQL에서_거부된다() {
        long userId = insertUser("migration-user");
        long teamId = insertTeam("MIGRATION-TEAM");
        long directTodoId = insertTodo(teamId, userId, "DIRECT");

        long directWorkItemId = insertWorkItem(directTodoId, userId, "DIRECT", null, null, 0, "proofs/one.png");

        assertThatThrownBy(() -> insertWorkItem(
                directTodoId, userId, "DIRECT", null, null, 1, "proofs/two.png"))
                .isInstanceOf(DataIntegrityViolationException.class);

        long taskTodoId = insertTodo(teamId, userId, "TASK");
        long firstTaskId = insertWorkItem(
                taskTodoId, userId, "TASK", "회의록", "2026-08-03 15:00:00", 0, "proofs/three.png");
        long secondTaskId = insertWorkItem(
                taskTodoId, userId, "TASK", "PPT", "2026-08-03 16:00:00", 1, "proofs/four.png");

        assertThat(firstTaskId).isNotEqualTo(secondTaskId);
        assertThatThrownBy(() -> insertWorkItem(
                taskTodoId, userId, "DIRECT", null, null, 2, "proofs/five.png"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertWorkItem(
                taskTodoId, userId, "TASK", "중복 사진", "2026-08-03 17:00:00", 2, "proofs/three.png"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update(
                "INSERT INTO todo_reactions (todo_work_item_id, user_id, reaction_type) VALUES (?, ?, 'LIKE')",
                directWorkItemId,
                userId
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM todo_reactions WHERE todo_work_item_id = ?",
                Integer.class,
                directWorkItemId
        )).isEqualTo(1);
    }

    private long insertUser(String loginId) {
        jdbcTemplate.update("""
                INSERT INTO users (login_id, password, nickname, email, created_at, updated_at)
                VALUES (?, 'password', '마이그레이션', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, loginId, loginId + "@example.com");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private long insertTeam(String inviteCode) {
        jdbcTemplate.update("""
                INSERT INTO teams (team_name, invite_code, success_count, created_at, updated_at)
                VALUES ('마이그레이션 팀', ?, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, inviteCode);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM teams WHERE invite_code = ?", Long.class, inviteCode);
    }

    private long insertTodo(long teamId, long creatorId, String mode) {
        jdbcTemplate.update("""
                INSERT INTO todos (team_id, creator_id, title, deadline, mode, status, created_at, updated_at)
                VALUES (?, ?, '마이그레이션 Todo', '2026-08-03 18:00:00', ?, 'IN_PROGRESS',
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, teamId, creatorId, mode);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM todos WHERE team_id = ? AND mode = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                teamId,
                mode
        );
    }

    private long insertWorkItem(
            long todoId,
            long assigneeId,
            String type,
            String taskTitle,
            String deadline,
            int position,
            String proofImageKey
    ) {
        jdbcTemplate.update("""
                INSERT INTO todo_work_items (
                    todo_id, assignee_id, type, task_title, deadline, position, status, proof_image_key,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, todoId, assigneeId, type, taskTitle, deadline, position, proofImageKey);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM todo_work_items WHERE todo_id = ? AND proof_image_key = ?",
                Long.class,
                todoId,
                proofImageKey
        );
    }
}
