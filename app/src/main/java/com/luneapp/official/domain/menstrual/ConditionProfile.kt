package com.luneapp.official.domain.menstrual

import com.luneapp.official.domain.settings.UserStatus

/**
 * ConditionProfile — 將 UserStatus 映射為演算法行為的核心引擎。
 *
 * 這是整個健康情境系統的大腦：每一種 UserStatus 都對應一套精確的演算法參數，
 * 讓預測、排卵計算、異常值過濾都能依據使用者的醫療情境做出適切的調整。
 *
 * 設計原則：
 * - 對用戶透明：UI 只顯示 9 大類，但底層依據精確情境調整
 * - 優雅降級：情境越複雜，系統越謹慎，信心分數越低，免責聲明越清楚
 * - 不替代醫療：系統只是記錄與追蹤工具，不做診斷
 */
data class ConditionProfile(
    /**
     * 有效週期長度範圍（用於異常值過濾）。
     * 範例：規律用戶為 21..35，PCOS 用戶為 21..180（允許超長週期）
     */
    val validCycleLengthRange: IntRange,

    /**
     * 有效黃體期長度範圍（用於排卵推算）。
     * 預設 10..16 天（醫學文獻範圍）。
     * 特殊情況（如不規律激素）可擴展或禁用排卵計算。
     */
    val validLutealRange: IntRange,

    /**
     * 是否啟用自動預測。
     * false = 記錄模式，不做週期預測（適用於閉經、GAHT 等）
     */
    val predictionsEnabled: Boolean,

    /**
     * 是否啟用自動確認（auto-confirm）。
     * 預測週期過期 3 天後自動轉為已確認記錄。
     * 對不規律用戶關閉，因為她們可能真的有長期閉經。
     */
    val autoConfirmEnabled: Boolean,

    /**
     * 是否啟用排卵窗口計算與顯示。
     * 荷爾蒙避孕、GAHT 等不適合顯示排卵資訊。
     */
    val ovulationTrackingEnabled: Boolean,

    /**
     * 基礎信心分數乘數（0.0~1.0）。
     * 最終信心分數 = 數據計算信心 × 此乘數。
     * 情境越不確定，乘數越低。
     */
    val confidenceMultiplier: Float,

    /**
     * 預測顯示的保護等級。
     * NONE = 正常顯示，LOW = 加免責聲明，HIGH = 強烈建議就醫提示
     */
    val warningLevel: WarningLevel,

    /**
     * 建議優先追蹤的項目（用於 UI 提示和 LogDaySheet 預設展開）
     */
    val suggestedTracking: Set<TrackingFocus>,

    /**
     * 週期天數上限（UI 保護，防止計算到離譜的天數）
     * 例如：PCOS 可到 180，規律用戶上限 60
     */
    val maxCycleDayDisplay: Int = 60,

    /**
     * 是否在統計頁面顯示「不規律分析」而非標準週期統計
     */
    val showIrregularAnalysis: Boolean = false,
)

enum class WarningLevel {
    /** 不需要特別警告 */
    NONE,
    /** 輕度提示：「預測僅供參考」 */
    LOW,
    /** 中度：「由於您的情況，預測準確性較低，建議記錄為主」 */
    MEDIUM,
    /** 高度：「此模式不顯示預測；請以記錄為主並定期回診」 */
    HIGH,
}

enum class TrackingFocus {
    FLOW_INTENSITY,         // 出血量
    BLEEDING_TYPE,          // 出血性質（點狀/異常）
    PAIN_SEVERITY,          // 疼痛強度
    MOOD,                   // 情緒
    SYMPTOMS,               // 一般症狀
    MEDICATIONS,            // 藥物記錄
    CLINICAL_NOTES,         // 臨床備注（實驗室數值等）
    BBT,                    // 基礎體溫（未來擴展）
    LH_TEST,                // LH 測試條（未來擴展）
    WEIGHT,                 // 體重（PCOS、飲食失調相關）
    STRESS_LEVEL,           // 壓力程度
    SLEEP_QUALITY,          // 睡眠品質
    EXERCISE,               // 運動量
}

/**
 * 核心映射函數：UserStatus → ConditionProfile
 *
 * 這裡是所有醫療情境邏輯的集中地。
 * 每個 UserStatus 都有精確、有依據的演算法參數配置。
 */
fun UserStatus.toConditionProfile(): ConditionProfile = when (this) {

    // ─────────────────────────────────────────────────────────────────
    // 規律週期：標準演算法，無限制
    // ─────────────────────────────────────────────────────────────────
    UserStatus.REGULAR -> ConditionProfile(
        validCycleLengthRange = 21..35,        // ACOG 定義正常範圍
        validLutealRange = 10..16,
        predictionsEnabled = true,
        autoConfirmEnabled = true,
        ovulationTrackingEnabled = true,
        confidenceMultiplier = 1.0f,
        warningLevel = WarningLevel.NONE,
        suggestedTracking = setOf(
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.MOOD,
            TrackingFocus.SYMPTOMS,
        ),
        maxCycleDayDisplay = 45,
        showIrregularAnalysis = false,
    )

    // ─────────────────────────────────────────────────────────────────
    // 多囊性卵巢症候群（PCOS）
    // 特徵：排卵障礙，週期 <21 或 >35（甚至 >90 天），雄激素過多
    // 演算法調整：放寬異常值範圍，降低信心，停用自動確認，顯示不規律分析
    // ─────────────────────────────────────────────────────────────────
    UserStatus.PCOS -> ConditionProfile(
        validCycleLengthRange = 21..180,       // PCOS 週期可達數月
        validLutealRange = 10..16,
        predictionsEnabled = true,             // 保留預測但信心低
        autoConfirmEnabled = false,            // 閉經期間不自動確認
        ovulationTrackingEnabled = true,       // 保留但加低信心說明
        confidenceMultiplier = 0.45f,          // 信心大幅降低
        warningLevel = WarningLevel.MEDIUM,
        suggestedTracking = setOf(
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.BLEEDING_TYPE,       // 點狀出血重要
            TrackingFocus.SYMPTOMS,
            TrackingFocus.WEIGHT,              // 體重與 PCOS 高度相關
            TrackingFocus.STRESS_LEVEL,
        ),
        maxCycleDayDisplay = 180,
        showIrregularAnalysis = true,
    )

    // ─────────────────────────────────────────────────────────────────
    // 子宮內膜異位症
    // 特徵：週期長度可能正常，但出血量大、疼痛劇烈、有點狀出血
    // 演算法調整：週期長度標準，但強調出血性質追蹤，降低中度信心
    // ─────────────────────────────────────────────────────────────────
    UserStatus.ENDOMETRIOSIS -> ConditionProfile(
        validCycleLengthRange = 21..45,        // 週期長度通常正常
        validLutealRange = 10..16,
        predictionsEnabled = true,
        autoConfirmEnabled = true,
        ovulationTrackingEnabled = true,
        confidenceMultiplier = 0.75f,          // 週期較規律，信心中等
        warningLevel = WarningLevel.LOW,
        suggestedTracking = setOf(
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.BLEEDING_TYPE,       // 點狀出血與異常出血
            TrackingFocus.PAIN_SEVERITY,       // 疼痛是核心症狀
            TrackingFocus.MOOD,
            TrackingFocus.CLINICAL_NOTES,      // CA-125 等實驗室數值
        ),
        maxCycleDayDisplay = 60,
        showIrregularAnalysis = false,
    )

    // ─────────────────────────────────────────────────────────────────
    // 荷爾蒙避孕
    // 特徵：出血為「撤退性出血」，非真正月經；無排卵
    // 演算法調整：停用排卵計算，降低預測信心，提示撤退性出血說明
    // ─────────────────────────────────────────────────────────────────
    UserStatus.HORMONAL_CONTRACEPTION -> ConditionProfile(
        validCycleLengthRange = 21..35,
        validLutealRange = 10..16,
        predictionsEnabled = true,             // 可預測撤退性出血時間
        autoConfirmEnabled = true,
        ovulationTrackingEnabled = false,      // ⚠️ 荷爾蒙避孕無真正排卵
        confidenceMultiplier = 0.70f,
        warningLevel = WarningLevel.LOW,
        suggestedTracking = setOf(
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.BLEEDING_TYPE,
            TrackingFocus.MOOD,
            TrackingFocus.MEDICATIONS,         // 記錄避孕藥種類劑量
        ),
        maxCycleDayDisplay = 45,
        showIrregularAnalysis = false,
    )

    // ─────────────────────────────────────────────────────────────────
    // 甲狀腺疾病（亢進/低下）
    // 特徵：干擾下視丘-腦下垂體-卵巢軸，週期縮短、延長或閉經
    // 演算法調整：放寬範圍，中等信心，建議記錄甲狀腺相關症狀
    // ─────────────────────────────────────────────────────────────────
    UserStatus.THYROID_DISORDER -> ConditionProfile(
        validCycleLengthRange = 18..60,        // 甲亢可縮短，甲低可延長
        validLutealRange = 10..16,
        predictionsEnabled = true,
        autoConfirmEnabled = false,            // 甲狀腺疾病波動大
        ovulationTrackingEnabled = true,
        confidenceMultiplier = 0.55f,
        warningLevel = WarningLevel.MEDIUM,
        suggestedTracking = setOf(
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.SYMPTOMS,            // 心悸、怕冷/怕熱等
            TrackingFocus.SLEEP_QUALITY,
            TrackingFocus.CLINICAL_NOTES,      // TSH、T3、T4 數值
        ),
        maxCycleDayDisplay = 90,
        showIrregularAnalysis = true,
    )

    // ─────────────────────────────────────────────────────────────────
    // 停經過渡期（更年期前期）
    // 特徵：週期長度高度不穩定，從縮短到延長，最終閉經
    // 演算法調整：大幅放寬範圍，低信心，強調記錄功能
    // ─────────────────────────────────────────────────────────────────
    UserStatus.PERIMENOPAUSE -> ConditionProfile(
        validCycleLengthRange = 14..90,        // 停經前週期高度不穩定
        validLutealRange = 8..18,              // 黃體期也可能異常
        predictionsEnabled = true,             // 保留但高度不確定
        autoConfirmEnabled = false,
        ovulationTrackingEnabled = true,
        confidenceMultiplier = 0.30f,          // 信心非常低
        warningLevel = WarningLevel.MEDIUM,
        suggestedTracking = setOf(
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.BLEEDING_TYPE,
            TrackingFocus.SYMPTOMS,            // 潮熱、盜汗、情緒
            TrackingFocus.MOOD,
            TrackingFocus.SLEEP_QUALITY,
            TrackingFocus.CLINICAL_NOTES,      // FSH、雌激素數值
        ),
        maxCycleDayDisplay = 120,
        showIrregularAnalysis = true,
    )

    // ─────────────────────────────────────────────────────────────────
    // 產後/哺乳期
    // 特徵：泌乳激素驅動的閉經，週期回歸時間不可預測
    // 演算法調整：停用自動確認，保留記錄功能，等待週期回歸
    // ─────────────────────────────────────────────────────────────────
    UserStatus.POSTPARTUM -> ConditionProfile(
        validCycleLengthRange = 21..60,        // 回歸初期可能不規律
        validLutealRange = 10..16,
        predictionsEnabled = false,            // 哺乳期閉經，無預測
        autoConfirmEnabled = false,
        ovulationTrackingEnabled = false,      // 泌乳激素抑制排卵
        confidenceMultiplier = 0.20f,
        warningLevel = WarningLevel.HIGH,
        suggestedTracking = setOf(
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.BLEEDING_TYPE,
            TrackingFocus.MOOD,                // 產後憂鬱追蹤
            TrackingFocus.SLEEP_QUALITY,
        ),
        maxCycleDayDisplay = 90,
        showIrregularAnalysis = true,
    )

    // ─────────────────────────────────────────────────────────────────
    // 腫瘤科—泰莫西芬（抗雌激素治療）
    // 特徵：選擇性雌激素受體調節劑，常引起閉經或不規律出血
    // ⚠️ 重要：泰莫西芬用戶的任何異常子宮出血都需要立即就醫
    // 演算法調整：停用大多數預測，強調異常出血記錄，顯示高警告
    // ─────────────────────────────────────────────────────────────────
    UserStatus.ONCOLOGY_TAMOXIFEN -> ConditionProfile(
        validCycleLengthRange = 21..180,
        validLutealRange = 10..16,
        predictionsEnabled = false,            // 泰莫西芬不適合週期預測
        autoConfirmEnabled = false,
        ovulationTrackingEnabled = false,
        confidenceMultiplier = 0.10f,
        warningLevel = WarningLevel.HIGH,
        suggestedTracking = setOf(
            TrackingFocus.BLEEDING_TYPE,       // ⚠️ 最重要：任何出血都記錄
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.PAIN_SEVERITY,       // 骨盆疼痛
            TrackingFocus.MEDICATIONS,         // 泰莫西芬劑量記錄
            TrackingFocus.CLINICAL_NOTES,      // CA-125、子宮內膜厚度等
        ),
        maxCycleDayDisplay = 180,
        showIrregularAnalysis = true,
    )

    // ─────────────────────────────────────────────────────────────────
    // 腫瘤科—其他治療（化療、芳香環酶抑制劑、卵巢抑制）
    // 特徵：化療常引起暫時或永久閉經，芳香環酶抑制劑停止雌激素產生
    // 演算法調整：完全記錄模式，無預測
    // ─────────────────────────────────────────────────────────────────
    UserStatus.ONCOLOGY_OTHER -> ConditionProfile(
        validCycleLengthRange = 21..180,
        validLutealRange = 10..16,
        predictionsEnabled = false,
        autoConfirmEnabled = false,
        ovulationTrackingEnabled = false,
        confidenceMultiplier = 0.10f,
        warningLevel = WarningLevel.HIGH,
        suggestedTracking = setOf(
            TrackingFocus.BLEEDING_TYPE,
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.SYMPTOMS,
            TrackingFocus.MEDICATIONS,         // 化療用藥記錄
            TrackingFocus.CLINICAL_NOTES,
        ),
        maxCycleDayDisplay = 180,
        showIrregularAnalysis = true,
    )

    // ─────────────────────────────────────────────────────────────────
    // 其他不規律情況（使用者自填）
    // 涵蓋：子宮肌瘤、腺肌症、POI、vWD、自體免疫、飲食失調等
    // 演算法調整：保守設定，中等信心，鼓勵記錄
    // ─────────────────────────────────────────────────────────────────
    UserStatus.OTHER_IRREGULAR -> ConditionProfile(
        validCycleLengthRange = 14..90,
        validLutealRange = 8..18,
        predictionsEnabled = true,
        autoConfirmEnabled = false,
        ovulationTrackingEnabled = true,
        confidenceMultiplier = 0.40f,
        warningLevel = WarningLevel.MEDIUM,
        suggestedTracking = setOf(
            TrackingFocus.FLOW_INTENSITY,
            TrackingFocus.BLEEDING_TYPE,
            TrackingFocus.PAIN_SEVERITY,
            TrackingFocus.SYMPTOMS,
            TrackingFocus.MEDICATIONS,
            TrackingFocus.CLINICAL_NOTES,
        ),
        maxCycleDayDisplay = 120,
        showIrregularAnalysis = true,
    )
}

/**
 * 依據 ConditionProfile 決定有效的週期長度（用於異常值過濾）
 */
fun ConditionProfile.isValidCycleLength(days: Int): Boolean =
    days in validCycleLengthRange

/**
 * 依據 ConditionProfile 計算信心分數乘數後的最終信心
 */
fun ConditionProfile.applyConfidenceMultiplier(rawScore: Float): Float =
    (rawScore * confidenceMultiplier).coerceIn(0f, 1f)

/**
 * 是否需要在 UI 顯示預測免責聲明
 */
val ConditionProfile.requiresDisclaimer: Boolean
    get() = warningLevel != WarningLevel.NONE

/**
 * 是否完全停用預測顯示（高警告等級）
 */
val ConditionProfile.suppressAllPredictions: Boolean
    get() = warningLevel == WarningLevel.HIGH && !predictionsEnabled
