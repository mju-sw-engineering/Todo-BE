package com.todo.domain.team.repository;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.entity.TeamMemberRole;
import com.todo.domain.user.entity.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TeamMemberRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Test
    void 본인을_제외한_팀원을_user와_함께_한_번에_조회한다() {
        Team team = entityManager.persist(Team.create("팀", null, "INVITE1"));
        User me = entityManager.persist(User.create("user1", "password", "나", null));
        User other = entityManager.persist(User.create("user2", "password", "동료", null));
        entityManager.persist(TeamMember.create(team, me, TeamMemberRole.LEADER));
        entityManager.persist(TeamMember.create(team, other, TeamMemberRole.MEMBER));
        entityManager.flush();
        entityManager.clear();

        List<TeamMember> members = teamMemberRepository.findByTeamIdExcludingUser(team.getId(), me.getId());

        assertThat(members).hasSize(1);
        assertThat(Hibernate.isInitialized(members.get(0).getUser())).isTrue();
        assertThat(members.get(0).getUser().getLoginId()).isEqualTo("user2");
    }
}
