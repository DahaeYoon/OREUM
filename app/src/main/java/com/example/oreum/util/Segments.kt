package com.example.oreum.util

import android.view.ViewGroup
import androidx.core.view.children

/**
 * 프로토타입의 세그먼트(.seg) · 글자 탭(.g4-pd)처럼, 묶음 안의 칸 하나만 '선택됨'으로 표시해요.
 * 칸 모양은 XML 스타일(Widget.OREUM.SegItem · TabItem)의 selected 상태가 바꿔요.
 */
fun ViewGroup.selectChild(index: Int) {
    children.forEachIndexed { i, child -> child.isSelected = i == index }
}

/** 칸을 누르면 그 순서(0부터)를 알려 줘요. */
fun ViewGroup.onChildClick(onClick: (Int) -> Unit) {
    children.forEachIndexed { i, child -> child.setOnClickListener { onClick(i) } }
}
