package com.example.oreum.data.model

/** 로그인 · 회원가입에서 생길 수 있는 오류. 화면에서 알맞은 문구(strings.xml)로 바꿔 보여 줘요. */
enum class AuthError {
    EMAIL_EMPTY,
    EMAIL_FORMAT,
    PASSWORD_EMPTY,
    PASSWORD_RULE,
    PASSWORD_MISMATCH,
    NICKNAME_EMPTY,
    NICKNAME_LENGTH,
    TERMS_REQUIRED,
    WRONG_CREDENTIALS,
    EMAIL_TAKEN,
    /** 인터넷 연결 문제 */
    NETWORK,
    /** 짧은 시간에 너무 많이 시도함 */
    TOO_MANY_REQUESTS,
    /** 그 밖의 서버 오류 */
    UNKNOWN,
}
