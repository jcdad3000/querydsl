package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.aspectj.weaver.ast.Expr;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
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

	@Test
	public void basicCase(){
		List<String> result = queryFactory
				.select(member.age
						.when(10).then("열살")
						.when(20).then("스무살")
						.otherwise("기타")
				)
				.from(member)
				.fetch();
		for (String s : result) {
			System.out.println("s = " + s);
		}

	}

	@Test
	public void complexCase(){
		List<String> result = queryFactory
				.select(new CaseBuilder()
						.when(member.age.between(0, 20)).then("0~20")
						.when(member.age.between(21, 30)).then("21~30")
						.otherwise("others"))
				.from(member)
				.fetch();
		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void constant(){
		List<Tuple> result = queryFactory
				.select(member.username, Expressions.constant("A"))
				.from(member)
				.fetch();
		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	@Test
	public void concat(){
		//{username}_{age}
		List<String> result = queryFactory
				.select(member.username.concat("_").concat(member.age.stringValue()))
				.from(member)
				.where(member.username.eq("member1"))
				.fetch();
		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void simpleProjection(){
		List<String> result = queryFactory
				.select(member.username)
				.from(member)
				.fetch();
		System.out.println("result = " + result);

	}

	@Test
	public void tupleProjection(){
		List<Tuple> result = queryFactory
				.select(member.username, member.age)
				.from(member)
				.fetch();
		for (Tuple tuple : result) {
			String username = tuple.get(member.username);
			Integer age = tuple.get(member.age);
			System.out.println("username = " + username);
			System.out.println("age = " + age);
		}
	}
	@Test
	public void findDtoByJPQL(){
		List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username,m.age) from Member m", MemberDto.class)
				.getResultList();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}
	@Test
	public void findDtoBySetter(){
		List<MemberDto> result = queryFactory
				.select(Projections.bean(MemberDto.class, member.username, member.age))
				.from(member)
				.fetch();
		for (MemberDto memberDto : result) {

			System.out.println("memberDto = " + memberDto);

		}
	}

	@Test
	public void findDtoByField(){
		List<MemberDto> result = queryFactory
				.select(Projections.fields(MemberDto.class, member.username, member.age))
				.from(member)
				.fetch();
		for (MemberDto memberDto : result) {

			System.out.println("memberDto = " + memberDto);

		}
	}
	@Test
	public void findDtoByConstructor(){
		List<UserDto> result = queryFactory
				.select(Projections.constructor(UserDto.class, member.username, member.age))
				.from(member)
				.fetch();
		for (UserDto memberDto : result) {

			System.out.println("memberDto = " + memberDto);

		}
	}
	@Test
	public void findUserDtoByField(){
		QMember memberSub = new QMember("memberSub");
		List<UserDto> result = queryFactory
				.select(Projections.fields(UserDto.class, member.username.as("name"),
						ExpressionUtils.as(JPAExpressions
								.select(memberSub.age.max()).from(memberSub),"age")
				))
				.from(member)
				.fetch();
		for (UserDto memberDto : result) {

			System.out.println("memberDto = " + memberDto);

		}
	}

	@Test
	public void findDtoByQueryProjection(){
		List<MemberDto> result = queryFactory
				.select(new QMemberDto(member.username, member.age)) //생성자가 사전에 생성되기때문에 인자를 잘못 입력했을때 컴파일 시점에 오류를 감지할 수 있음
				//Qfile을 생성해야한다는 단점을 가지고 있음
				//DTO에 QueryDSL에 대한 의존성을 가지게 됨
				.from(member)
				.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	public void dynamicQueryBooleanBuilder(){
		String usernameParam = "member1";
		Integer ageParam =10;

		List<Member> result = searchMember1(usernameParam,ageParam);
		assertThat(result.size()).isEqualTo(1);

	}

	private List<Member> searchMember1(String usernameParam, Integer ageParam) {
		BooleanBuilder builder = new BooleanBuilder();
		if (usernameParam !=null){
			builder.and(member.username.eq(usernameParam));
		}

		if(ageParam !=null){
			builder.and(member.age.eq(ageParam));
		}

		return queryFactory
				.selectFrom(member)
				.where(builder)
				.fetch();
	}

	@Test
	public void dynamicQuery_WhereParam(){
		String usernameParam = "member1";
		Integer ageParam =10;

		List<Member> result = searchMember2(usernameParam,ageParam);
		assertThat(result.size()).isEqualTo(1);

	}

	private List<Member> searchMember2(String userCond, Integer ageCond) {
		return queryFactory
				.selectFrom(member)
				.where(usernameEq(userCond),ageEq(ageCond))
				.fetch();
	}

	private BooleanExpression ageEq(Integer ageCond) {
		if(ageCond==null){
			return null;
		}
		return member.age.eq(ageCond);
	}

	private BooleanExpression usernameEq(String userCond) {
		return userCond!=null ?  member.username.eq(userCond): null;
	}

	private BooleanExpression allEq(String usernameCond, Integer ageCond){
		return usernameEq(usernameCond).and(ageEq(ageCond));
	}

	@Test
	public void bulkUpdate(){
		long count = queryFactory
				.update(member)
				.set(member.username, "비회원")
				.where(member.age.lt(28))
				.execute();

		em.flush();
		em.clear();
		List<Member> result = queryFactory
				.selectFrom(member)
				.fetch();
		for (Member member1 : result) {
			System.out.println("member1 = " + member1);
		}
	}
	
	@Test
	public void bulkAdd(){
		long count = queryFactory
				.update(member)
				.set(member.age, member.age.add(1))
				.execute();
	}

	@Test
	public void bulkDelete(){
		long count = queryFactory
				.delete(member)
				.where(member.age.gt(18))
				.execute();
	}

	@Test
	public void sqlFunction(){
		List<String> result = queryFactory
				.select(Expressions.stringTemplate("function('replace',{0},{1},{2})",
						member.username, "member", "M"))
				.from(member)
				.fetch();
		for (String s : result) {
			System.out.println("s = " + s);
		}

	}

	@Test
	public void sqlFunction2(){
		List<String> result = queryFactory
				.select(member.username)
				.from(member)
				//.where(member.username.eq(Expressions.stringTemplate("function('lower',{0})", member.username)))
				.where(member.username.eq(member.username.lower()))
				.fetch();
		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

}

