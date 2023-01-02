package study.querydsl;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import study.querydsl.entity.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import javax.transaction.Transactional;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
@Commit
class QuerydslApplicationTests {
	@PersistenceContext
	EntityManager em;
	JPAQueryFactory queryFactory;

	@BeforeEach
	public void before(){
		queryFactory = new JPAQueryFactory(em);

		Team teamA = new Team("teamA");
		Team teamB = new Team("teamB");
		em.persist(teamA);
		em.persist(teamB);
		Member member1 = new Member("member1", 10, teamA);
		Member member2 = new Member("member2", 20, teamA);
		Member member3 = new Member("member3", 30, teamB);
		Member member4 = new Member("member4", 40, teamB);
		em.persist(member1);
		em.persist(member2);
		em.persist(member3);
		em.persist(member4);

	}
	@Test
	void contextLoads() {
		Hello hello = new Hello();
		em.persist(hello);

		JPAQueryFactory query = new JPAQueryFactory(em);
		QHello qHello = new QHello("h");

		Hello result = query.selectFrom(qHello).fetchOne();

		assertThat(result.getId()).isEqualTo(hello.getId());
		assertThat(result).isEqualTo(hello);

	}
	@Test
	public void join_on_filtering(){
		List<Tuple> result = queryFactory
				.select(member, team)
				.from(member)
				.join(member.team, team)
				.where(team.name.eq("teamA"))
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);

		}

	}
	@Test
	public void join_on_no_relation(){
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));
		em.persist(new Member("teamC"));

		List<Tuple> result = queryFactory
				.select(member, team)
				.from(member)
				.leftJoin(team).on(member.username.eq(team.name))
				.where(member.username.eq(team.name))
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}
	@PersistenceUnit
	EntityManagerFactory emf;
	@Test
	public void fetchJoinNo(){
		em.flush();
		em.clear();

		Member findMember = queryFactory
				.selectFrom(member)
				.where(member.username.eq("member1"))
				.fetchOne();

		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
		assertThat(loaded).as("페치 조인 미적용").isFalse();
	}

	@Test
	public void fetchJoinUse(){
		em.flush();
		em.clear();

		Member findMember = queryFactory
				.selectFrom(member)
				.join(member.team,team).fetchJoin()
				.where(member.username.eq("member1"))
				.fetchOne();

		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
		assertThat(loaded).as("페치 조인 미적용").isTrue();
	}

	@Test
	public void subQuery(){
		QMember memberSub = new QMember("memberSub");
		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.eq(
						select(memberSub.age.max())
								.from(memberSub)
				)).fetch();

		assertThat(result).extracting("age").containsExactly(40);
	}

	/**
	 * 나이가 평균보다 높은 회원 조회
	 */
	@Test
	public void subQueryGoe(){
		QMember memberSub = new QMember("memberSub");
		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.goe(
						select(memberSub.age.avg())
								.from(memberSub)
				)).fetch();

		assertThat(result).extracting("age").containsExactly(30,40);
	}

	@Test
	public void subQueryIn(){
		QMember memberSub = new QMember("memberSub");
		List<Member> result = queryFactory
				.selectFrom(member)
				.where(member.age.in(
						select(memberSub.age)
								.from(memberSub)
								.where(memberSub.age.gt(10))
				)).fetch();

		assertThat(result).extracting("age").containsExactly(20,30,40);
	}

	@Test
	public void selectSubquery(){
		QMember memberSub = new QMember("memberSub");
		List<Tuple> result = queryFactory
				.select(member.username,
						select(memberSub.age.avg())
								.from(memberSub))
				.from(member)
				.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}
}
