package com.isroot.lmbridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isroot.lmbridge.inference.GenerationResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LMBridgeClientTest {

    private lateinit var context: Context
    private lateinit var client: LMBridgeClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Builder를 사용하여 클라이언트 생성 (private constructor 해결)
        client = LMBridgeClient.Builder(context)
            .setMaxNumTokens(512) // 테스트를 위해 낮게 설정
            .build()
    }

    @Test
    fun testInitialization() = runBlocking {
        client.initialize()
        // initialize()가 예외 없이 완료되면 성공으로 간주
        assertNotNull(client)
    }

    @Test
    fun testSimpleGeneration() = runBlocking {
        client.initialize()
        
        val prompt = "Hello, who are you?"
        val results = client.generate(prompt).toList()
        
        assertTrue("Results should not be empty", results.isNotEmpty())
        assertTrue("Results should end with Done", results.last() is GenerationResult.Done)
    }

    @Test
    fun testLongPromptChunking() = runBlocking {
        client.initialize()
        
        // maxNumTokens(512)를 확실히 초과하는 매우 긴 프롬프트 생성
        val longPrompt = "당신은 데이터 추출 전문가입니다. 아래 제공된 [지시 사항]을 참고하여, [KBO 스코어보드 현황] 텍스트에서 경기 정보를 추출하여 지정된 [JSON 양식]으로 변환하세요.\n" +
                "            # 지시 사항:\n" +
                "            1. 모든 경기 정보를 추출하여 \"games\" 리스트 안에 객체로 담으세요.\n" +
                "            2. homeTeam, awayTeam 이름을 정확히 매칭하세요. (예: 두산, LG, NC 등)\n" +
                "            3. homeScore, awayScore는 스코어보드 표의 'R' 항목 값을 사용하되, 경기 전이거나 비어있으면 0으로 처리하세요.\n" +
                "            4. status는 \"경기전\", \"종료\", \"N회초\" 등 상태를 입력하고, stadium은 \"잠실\", \"대구\" 등 구장명을 입력하세요.\n" +
                "            5. 결과물은 반드시 공백과 줄바꿈이 없는 '한 줄의 Compact JSON'으로만 출력하고, 다른 설명은 생략하세요.\n" +
                "                                                                                                    \n" +
                "            # JSON 양식:\n" +
                "            {\"games\":[{\"homeTeam\":\"\",\"awayTeam\":\"\",\"homeScore\":0,\"awayScore\":0,\"status\":\"\",\"stadium\":\"\"}]}\n" +
                "                                                                                                    \n" +
                "            # KBO 스코어보드 현황:\n" +
                "            - 로그인\n" +
                "- 회원가입\n" +
                "- ENGLISH\n" +
                "[](#none;)\n" +
                "##\n" +
                "- 일정・결과 경기일정・결과 게임센터 스코어보드 올스타전 국제대회 야구장 날씨\n" +
                "- 기록・순위 기록실 팀 순위 선수 순위 역대 기록 예상 달성 기록 기록 정정 현황 관중 현황\n" +
                "- 선수 선수 조회 선수 등록 현황 선수 이동 현황 수상 현황 레전드 40 경력증명서 신청\n" +
                "- 미디어・뉴스 하이라이트 뉴스 KBO 보도자료 대학생 마케터\n" +
                "- KBO KBO KBO 리그 2026 주요 규정∙규칙 경기운영제도 구단 소개 구단 변천사 티켓 안내 게시판 NOTICE 자주 하는 질문 규정・자료실 ABOUT KBO 조직・활동 KBO 로고 기록위원회 의무위원회 주요 사업・행사 2025 KBO 시상식 2026 신인 드래프트 미디어데이&팬페스트 기록강습회 수강신청 KBO 경기장 안전정책 KBO 리그 영상 구매 시각장애인 관람 지원\n" +
                "- 퓨처스리그 경기일정・결과 팀 순위 TOP5 기록실 선수 등록 현황\n" +
                "- KBO 마켓\n" +
                "- KBO 폰트\n" +
                "- 전체 메뉴 전체 메뉴 일정・결과 경기일정・결과 게임센터 스코어보드 올스타전 국제대회 야구장 날씨 기록・순위 기록실 팀 순위 선수 순위 역대 기록 예상 달성 기록 기록 정정 현황 관중 현황 선수 선수 조회 선수 등록 현황 선수 이동 현황 수상 현황 레전드 40 경력증명서 신청 미디어・뉴스 하이라이트 뉴스 KBO 보도자료 대학생 마케터 퓨처스리그 경기일정・결과 팀 순위 TOP5 기록실 선수 등록 현황 KBO KBO 리그 2026 주요 규정∙규칙 경기운영제도 구단 소개 구단 변천사 티켓 안내 게시판 NOTICE 자주 하는 질문 규정・자료실 ABOUT KBO 조직・활동 KBO 로고 기록위원회 의무위원회 주요 사업・행사 2025 KBO 시상식 2026 신인 드래프트 미디어데이&팬페스트 기록강습회 수강신청 KBO 경기장 안전정책 KBO 리그 영상 구매 시각장애인 관람 지원 닫기\n" +
                "## 일정・결과\n" +
                "- 경기일정・결과\n" +
                "- 게임센터\n" +
                "- 스코어보드\n" +
                "- 올스타전\n" +
                "- 국제대회\n" +
                "- 야구장 날씨\n" +
                "[](/Default.aspx)> [일정・결과](/Schedule/GameCenter/Main.aspx)> 스코어보드\n" +
                "## 스코어보드\n" +
                "-\n" +
                "- 2026.05.07(목)\n" +
                "-\n" +
                "![원정팀]()**두산****\n" +
                "**경기전**\n" +
                "****LG**![홈팀]()\n" +
                "![1루]()![2루]()![3루]()\n" +
                "- out\n" +
                "[](#)![리뷰]()\n" +
                "잠실 18:30\n" +
                "|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|\n" +
                "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n" +
                "|두산|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "|LG|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "![원정팀]()**NC****\n" +
                "**경기전**\n" +
                "****SSG**![홈팀]()\n" +
                "![1루]()![2루]()![3루]()\n" +
                "- out\n" +
                "[](#)![리뷰]()\n" +
                "문학 18:30\n" +
                "|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|\n" +
                "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n" +
                "|NC|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "|SSG|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "![원정팀]()**키움****\n" +
                "**경기전**\n" +
                "****삼성**![홈팀]()\n" +
                "![1루]()![2루]()![3루]()\n" +
                "- out\n" +
                "[](#)![리뷰]()\n" +
                "대구 18:30\n" +
                "|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|\n" +
                "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n" +
                "|키움|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "|삼성|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "![원정팀]()**롯데****\n" +
                "**경기전**\n" +
                "****KT**![홈팀]()\n" +
                "![1루]()![2루]()![3루]()\n" +
                "- out\n" +
                "[](#)![리뷰]()\n" +
                "수원 18:30\n" +
                "|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|\n" +
                "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n" +
                "|롯데|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "|KT|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "![원정팀]()**한화****\n" +
                "**경기전**\n" +
                "****KIA**![홈팀]()\n" +
                "![1루]()![2루]()![3루]()\n" +
                "- out\n" +
                "[](#)![리뷰]()\n" +
                "광주 18:30\n" +
                "|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|\n" +
                "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n" +
                "|한화|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "|KIA|-|-|-|-|-|-|-|-|-|-|-|-|||||\n" +
                "|---|\n" +
                "- 개인정보 처리방침\n" +
                "- 문자중계\n" +
                "- 고객질문\n" +
                "- 사이트맵\n" +
                "##\n" +
                "(사)한국야구위원회 | 서울시 강남구 강남대로 278 | 02)3460-4600\n" +
                "Copyrightⓒ KBO, All Rights Reserved. ![w3c xhtml 3.0, Verisign]()"
        
        // True Chunking 로직이 정상 작동한다면, 
        // 내부적으로 세션을 유지하며 Prefill을 수행하고 마지막에만 Decode하여 답을 내놓아야 함.
        val results = client.generate(longPrompt).toList()
        
        assertTrue("Results should not be empty for long prompt", results.isNotEmpty())
        assertTrue("Results should end with Done", results.last() is GenerationResult.Done)
        
        // 결과물 중 Token이 포함되어 있는지 확인
        val hasTokens = results.any { it is GenerationResult.Token }
        assertTrue("Should have generated at least one token", hasTokens)
    }

    @Test
    fun testGenerateWithTexts() = runBlocking {
        client.initialize()
        
        val texts = listOf("First part of the context.", "Second part of the context.", "Now answer: what is the summary?")
        val results = client.generate(texts.joinToString { " $it" }).toList()
        
        assertTrue("Results should not be empty", results.isNotEmpty())
        assertTrue("Results should end with Done", results.last() is GenerationResult.Done)
    }
}
