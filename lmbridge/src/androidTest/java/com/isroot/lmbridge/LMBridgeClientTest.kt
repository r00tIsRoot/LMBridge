package com.isroot.lmbridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isroot.lmbridge.inference.GenerationResult
import com.isroot.lmbridge.inference.ModelInferenceManager
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
        val longPrompt = """
            앞으로 주어질 정보는 여러 경기가 포함될 수 있는 KBO 스코어보드입니다.
            모든 경기 정보를 먼저 경기 목록을 모두 나열한 뒤, 다음과 같이 줄바꿈이 없는 순수한 JSON으로 변환: {\"games\":[{\"homeTeam\":\"\",\"awayTeam\":\"\",\"homeScore\":0,\"awayScore\":0,\"status\":\"\",\"stadium\":\"\"}]}
                                                                                                    
            # KBO 스코어보드 현황:
            ```markdown
            - 로그인
            - 회원가입
            - ENGLISH
            [](#none;)
            ##
            - 일정・결과 경기일정・결과 게임센터 스코어보드 올스타전 국제대회 야구장 날씨
            - 기록・순위 기록실 팀 순위 선수 순위 역대 기록 예상 달성 기록 기록 정정 현황 관중 현황
            - 선수 선수 조회 선수 등록 현황 선수 이동 현황 수상 현황 레전드 40 경력증명서 신청
            - 미디어・뉴스 하이라이트 뉴스 KBO 보도자료 Z-CREW
            - KBO KBO KBO 리그 2026 주요 규정∙규칙 경기운영제도 구단 소개 구단 변천사 티켓 안내 게시판 NOTICE 자주 하는 질문 규정・자료실 ABOUT KBO 조직・활동 KBO 로고 기록위원회 의무위원회 주요 사업・행사 2025 KBO 시상식 2026 신인 드래프트 미디어데이&팬페스트 기록강습회 수강신청 KBO 경기장 안전정책 KBO 리그 영상 구매 시각장애인 관람 지원
            - 퓨처스리그 경기일정・결과 팀 순위 TOP5 기록실 선수 등록 현황
            - KBO 마켓
            - KBO 폰트
            - 전체 메뉴 전체 메뉴 일정・결과 경기일정・결과 게임센터 스코어보드 올스타전 국제대회 야구장 날씨 기록・순위 기록실 팀 순위 선수 순위 역대 기록 예상 달성 기록 기록 정정 현황 관중 현황 선수 선수 조회 선수 등록 현황 선수 이동 현황 수상 현황 레전드 40 경력증명서 신청 미디어・뉴스 하이라이트 뉴스 KBO 보도자료 Z-CREW 퓨처스리그 경기일정・결과 팀 순위 TOP5 기록실 선수 등록 현황 KBO KBO 리그 2026 주요 규정∙규칙 경기운영제도 구단 소개 구단 변천사 티켓 안내 게시판 NOTICE 자주 하는 질문 규정・자료실 ABOUT KBO 조직・활동 KBO 로고 기록위원회 의무위원회 주요 사업・행사 2025 KBO 시상식 2026 신인 드래프트 미디어데이&팬페스트 기록강습회 수강신청 KBO 경기장 안전정책 KBO 리그 영상 구매 시각장애인 관람 지원 닫기
            ## 일정・결과
            - 경기일정・결과
            - 게임센터
            - 스코어보드
            - 올스타전
            - 국제대회
            - 야구장 날씨
            [](/Default.aspx)> [일정・결과](/Schedule/GameCenter/Main.aspx)> 스코어보드
            ## 스코어보드
            -
            - 2026.05.07(목)
            -
            ![원정팀]()**두산***0*
            **1회초**
            *0***LG**![홈팀]()
            ![1루]()![2루]()![3루]()
            0-0 0 out
            [](#)[]
            잠실 18:30
            |TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
            |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
            |두산|0|-|-|-|-|-|-|-|-|-|-|-|0|0|0|0|
            |---|
            |LG|-|-|-|-|-|-|-|-|-|-|-|-|0|0|0|0|
            |---|
            ![원정팀]()**NC***0*
            **1회초**
            *0***SSG**![홈팀]()
            ![1루]()![2루]()![3루]()
            - out
            [](#)[]
            문학 18:30
            |TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
            |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
            |NC|0|-|-|-|-|-|-|-|-|-|-|-|0|0|0|0|
            |---|
            |SSG|-|-|-|-|-|-|-|-|-|-|-|-|0|0|0|0|
            |---|
            ![원정팀]()**키움***0*
            **1회초**
            *0***삼성**![홈팀]()
            ![1루]()![2루]()![3루]()
            - out
            [](#)[]
            대구 18:30
            |TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
            |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
            |키움|0|-|-|-|-|-|-|-|-|-|-|-|0|0|0|0|
            |---|
            |삼성|-|-|-|-|-|-|-|-|-|-|-|-|0|0|0|0|
            |---|
            ![원정팀]()**롯데****
            **경기전**
            ****KT**![홈팀]()
            ![1루]()![2루]()![3루]()
            - out
            [](#)![리뷰]()
            수원 18:30
            |TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
            |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
            |롯데|-|-|-|-|-|-|-|-|-|-|-|-|||||
            |---|
            |KT|-|-|-|-|-|-|-|-|-|-|-|-|||||
            |---|
            ![원정팀]()**한화****
            **경기전**
            ****KIA**![홈팀]()
            ![1루]()![2루]()![3루]()
            - out
            [](#)![리뷰]()
            광주 18:30
            |TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
            |---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
            |한화|-|-|-|-|-|-|-|-|-|-|-|-|||||
            |---|
            |KIA|-|-|-|-|-|-|-|-|-|-|-|-|||||
            |---|
            - 개인정보 처리방침
            - 문자중계
            - 고객질문
            - 사이트맵
            ##
            (사)한국야구위원회 | 서울시 강남구 강남대로 278 | 02)3460-4600
            Copyrightⓒ KBO, All Rights Reserved. ![w3c xhtml 3.0, Verisign]()
        """.trimIndent()
        
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

    @Test
    fun testSplitChunk() = runBlocking {
        val manager = ModelInferenceManager(
            context = context
        )

        val prompt = """
                        KBO 스코어보드입니다. 존재하는 모든 경기 정보들을 다음 양식과 같은 순수한 JSON으로 변환:
            # 경기별 양식
            ```markdown
            {\"homeTeam\":\"\",\"awayTeam\":\"\",\"homeScore\":0,\"awayScore\":0,\"status\":\"2out\",\"stadium\":\"잠실\"}
            ```
            # 최종 양식
            ```markdown
            {\"games\":[{...}, {...}, ...]}
            ```
            # KBO 스코어보드
            ```markdown
            - 로그인
- 회원가입
- ENGLISH
[](#none;)
##
- 일정・결과 경기일정・결과 게임센터 스코어보드 올스타전 국제대회 야구장 날씨
- 기록・순위 기록실 팀 순위 선수 순위 역대 기록 예상 달성 기록 기록 정정 현황 관중 현황
- 선수 선수 조회 선수 등록 현황 선수 이동 현황 수상 현황 레전드 40 경력증명서 신청
- 미디어・뉴스 하이라이트 뉴스 KBO 보도자료 Z-CREW
- KBO KBO KBO 리그 2026 주요 규정∙규칙 경기운영제도 구단 소개 구단 변천사 티켓 안내 게시판 NOTICE 자주 하는 질문 규정・자료실 ABOUT KBO 조직・활동 KBO 로고 기록위원회 의무위원회 주요 사업・행사 2025 KBO 시상식 2026 신인 드래프트 미디어데이&팬페스트 기록강습회 수강신청 KBO 경기장 안전정책 KBO 리그 영상 구매 시각장애인 관람 지원
- 퓨처스리그 경기일정・결과 팀 순위 TOP5 기록실 선수 등록 현황
- KBO 마켓
- KBO 폰트
- 전체 메뉴 전체 메뉴 일정・결과 경기일정・결과 게임센터 스코어보드 올스타전 국제대회 야구장 날씨 기록・순위 기록실 팀 순위 선수 순위 역대 기록 예상 달성 기록 기록 정정 현황 관중 현황 선수 선수 조회 선수 등록 현황 선수 이동 현황 수상 현황 레전드 40 경력증명서 신청 미디어・뉴스 하이라이트 뉴스 KBO 보도자료 Z-CREW 퓨처스리그 경기일정・결과 팀 순위 TOP5 기록실 선수 등록 현황 KBO KBO 리그 2026 주요 규정∙규칙 경기운영제도 구단 소개 구단 변천사 티켓 안내 게시판 NOTICE 자주 하는 질문 규정・자료실 ABOUT KBO 조직・활동 KBO 로고 기록위원회 의무위원회 주요 사업・행사 2025 KBO 시상식 2026 신인 드래프트 미디어데이&팬페스트 기록강습회 수강신청 KBO 경기장 안전정책 KBO 리그 영상 구매 시각장애인 관람 지원 닫기
## 일정・결과
- 경기일정・결과
- 게임센터
- 스코어보드
- 올스타전
- 국제대회
- 야구장 날씨
[](/Default.aspx)> [일정・결과](/Schedule/GameCenter/Main.aspx)> 스코어보드
## 스코어보드
- 
- 2026.05.07(목)
- 
![원정팀]()**두산***0*
**2회초**
*0***LG**![홈팀]()
![1루]()![2루]()![3루]()
0-0 0 out
[](#)[]
잠실 18:30
|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
|두산|0|0|-|-|-|-|-|-|-|-|-|-|0|1|0|0|
|---|
|LG|0|-|-|-|-|-|-|-|-|-|-|-|0|1|0|0|
|---|
![원정팀]()**NC***0*
**2회초**
*0***SSG**![홈팀]()
![1루]()![2루]()![3루]()
1-1 0 out
[](#)[]
문학 18:30
|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
|NC|0|0|-|-|-|-|-|-|-|-|-|-|0|1|0|0|
|---|
|SSG|0|-|-|-|-|-|-|-|-|-|-|-|0|1|0|0|
|---|
![원정팀]()**키움***0*
**1회말**
*3***삼성**![홈팀]()
![1루]()![2루]()![3루]()
0-2 2 out
[](#)[]
대구 18:30
|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
|키움|0|-|-|-|-|-|-|-|-|-|-|-|0|1|0|0|
|---|
|삼성|3|-|-|-|-|-|-|-|-|-|-|-|3|3|0|1|
|---|
![원정팀]()**한화***1*
**2회초**
*0***KIA**![홈팀]()
![1루]()![2루]()![3루]()
1-2 0 out
[](#)[]
광주 18:30
|TEAM|1|2|3|4|5|6|7|8|9|10|11|12|R|H|E|B|
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
|한화|0|1|-|-|-|-|-|-|-|-|-|-|1|3|0|0|
|---|
|KIA|0|-|-|-|-|-|-|-|-|-|-|-|0|0|0|0|
|---|
- 개인정보 처리방침
- 문자중계
- 고객질문
- 사이트맵
##
(사)한국야구위원회 | 서울시 강남구 강남대로 278 | 02)3460-4600
Copyrightⓒ KBO, All Rights Reserved. ![w3c xhtml 3.0, Verisign]()
            ```
        """.trimIndent()

        val splitResult = manager.splitByTokenLimit(
            prompt,
            1024
        )

        assertTrue("Results should not be empty", splitResult.isNotEmpty())
        
        splitResult.forEachIndexed { index, chunk ->
            println("Chunk $index... (length: ${chunk.length})\n$chunk")
        }

    }
}
