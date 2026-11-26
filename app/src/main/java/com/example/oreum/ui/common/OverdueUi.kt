package com.example.oreum.ui.common

import androidx.fragment.app.Fragment
import com.example.oreum.R
import com.example.oreum.data.model.Goal
import com.example.oreum.data.repository.StudyRepositoryProvider
import com.example.oreum.databinding.DialogOverdueBinding
import com.example.oreum.domain.usecase.RoadmapProgress
import com.example.oreum.util.DateText
import com.example.oreum.util.showToast
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ceil

/**
 * 마감일이 지난 목표 정리하기 (홈 · 목표 상세에서 함께 써요).
 * ① 마감일 늘리기: 새 마감일을 고르면 다음 구간부터 새 기간에 맞춰 AI가 계획해요.
 * ② 여기까지로 마치기: 지금까지 한 만큼으로 목표를 끝내요 (정상 도착이 아니라 '마감 종료').
 */
object OverdueUi {

    fun show(fragment: Fragment, goal: Goal) {
        val ctx = fragment.requireContext()
        val repo = StudyRepositoryProvider.instance
        val remainHours = ceil(RoadmapProgress.of(goal, repo.rounds.value).remainingHours).toInt()
        val body = ctx.getString(
            R.string.overdue_body,
            goal.title,
            DateText.monthDay(DateText.toDate(goal.dueAt)),
            remainHours,
        )
        // 버튼 글자가 길어서 오른쪽에 몰리지 않게, 꽉 찬 버튼을 세로로 놓은 창을 써요
        val v = DialogOverdueBinding.inflate(fragment.layoutInflater)
        v.textOverdueBody.text = body
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.overdue_title)
            .setView(v.root)
            .create()
        v.btnOverdueExtend.setOnClickListener { dialog.dismiss(); pickNewDue(fragment, goal) }
        v.btnOverdueFinish.setOnClickListener { dialog.dismiss(); confirmFinish(fragment, goal) }
        v.btnOverdueLater.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /** 새 마감일: 내일부터 고를 수 있어요 */
    private fun pickNewDue(fragment: Fragment, goal: Goal) {
        val tomorrow = LocalDate.now().plusDays(1)
        fun utcMillis(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.ThemeOverlay_OREUM_DatePicker)
            .setTitleText(R.string.overdue_pick_title)
            .setPositiveButtonText(R.string.date_pick_ok)
            .setNegativeButtonText(R.string.study_form_cancel)
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setStart(utcMillis(tomorrow.withDayOfMonth(1)))
                    .setValidator(DateValidatorPointForward.from(utcMillis(tomorrow)))
                    .setOpenAt(utcMillis(tomorrow))
                    .build(),
            )
            .setSelection(utcMillis(tomorrow.plusDays(6)))
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            StudyRepositoryProvider.instance.extendDue(goal.id, DateText.endOfDayMillis(date))
            fragment.activity?.showToast(fragment.getString(R.string.toast_overdue_extended, DateText.monthDay(date)))
        }
        picker.show(fragment.childFragmentManager, "overdue_due")
    }

    private fun confirmFinish(fragment: Fragment, goal: Goal) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.overdue_finish_title)
            .setMessage(R.string.overdue_finish_body)
            .setNegativeButton(R.string.study_form_cancel, null)
            .setPositiveButton(R.string.overdue_finish) { _, _ ->
                StudyRepositoryProvider.instance.finishAtDue(goal.id)
                fragment.activity?.showToast(R.string.toast_overdue_finished)
            }
            .show()
    }
}
