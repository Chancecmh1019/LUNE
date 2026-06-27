package com.luneapp.official.domain.settings

/**
 * 使用者的健康情境分類。
 *
 * 設計哲學：
 * - 10 種精確條件，涵蓋最廣泛的臨床情境
 * - 每種條件都對應 ConditionProfile（演算法參數），而非只影響 isIrregular 旗標
 * - UI 將這 10 種條件整合為 9 大直觀類別（見 UserStatusGroup），
 *   讓用戶不需要醫學知識也能正確選擇
 *
 * isIrregular 語義：
 * - true  = 演算法需降低信心分數、調整過濾範圍、可能停用某些預測功能
 * - false = 標準演算法，完整功能
 */
enum class UserStatus(val isIrregular: Boolean) {

    /** 典型週期，無已知影響規律性的情況。完整演算法支援。 */
    REGULAR(isIrregular = false),

    /**
     * 產後 / 哺乳期。
     * 泌乳激素驅動的無排卵，常導致數月閉經。週期回歸時間不可預測。
     */
    POSTPARTUM(isIrregular = true),

    /**
     * 腫瘤科 — 泰莫西芬（選擇性雌激素受體調節劑）。
     * 常引起週期紊亂、閉經或誘發停經。
     * ⚠️ 警告：泰莫西芬治療期間的任何異常子宮出血都需立即就醫評估。
     */
    ONCOLOGY_TAMOXIFEN(isIrregular = true),

    /**
     * 腫瘤科 — 其他治療（化療、芳香環酶抑制劑、卵巢抑制療法）。
     * 此類藥物常導致閉經或不規則出血。
     */
    ONCOLOGY_OTHER(isIrregular = true),

    /**
     * 多囊性卵巢症候群（PCOS）。
     * 以少排卵或無排卵為特徵，週期範圍從 <21 天到 >35 天，甚至數月閉經。
     * 演算法放寬異常值過濾範圍（21..180 天）。
     */
    PCOS(isIrregular = true),

    /**
     * 子宮內膜異位症。
     * 可能引起大量出血、延長或不規律出血及慢性骨盆疼痛。
     * 週期長度本身可能正常，但出血性質常常異常。
     */
    ENDOMETRIOSIS(isIrregular = true),

    /**
     * 荷爾蒙避孕（複合口服避孕藥、純黃體素藥、荷爾蒙 IUD、植入劑或注射）。
     * 撤退性出血不是真正的月經週期；標準排卵預測不適用。
     */
    HORMONAL_CONTRACEPTION(isIrregular = true),

    /**
     * 甲狀腺疾病（甲狀腺功能亢進或低下）。
     * 干擾下視丘-腦下垂體-卵巢軸，常導致月經稀少、閉經或月經過多。
     */
    THYROID_DISORDER(isIrregular = true),

    /**
     * 停經過渡期 / 更年期前期。
     * 週期長度和出血量在停經前高度不穩定。FSH 升高及雌激素波動使預測不可靠。
     */
    PERIMENOPAUSE(isIrregular = true),

    /**
     * 其他不規律情況。
     * 涵蓋用戶知道影響週期但未列於上述選項的其他情況
     *（如：子宮肌瘤、子宮腺肌症、早發性卵巢衰竭、類血友病、自體免疫疾病、
     *  飲食失調、下視丘性閉經、GAHT 等）。
     */
    OTHER_IRREGULAR(isIrregular = true);

    companion object {
        fun fromValue(value: String?): UserStatus =
            entries.firstOrNull { it.name == value } ?: REGULAR
    }
}

/**
 * UI 顯示用的分組類別。
 *
 * 將 10 種精確的 UserStatus 映射到 9 個用戶直觀理解的大類，
 * 讓用戶在 Onboarding 時不需要醫學知識也能正確選擇。
 *
 * UI 流程：
 * 1. 用戶選擇 UserStatusGroup（9 張大卡）
 * 2. 若該 Group 只對應一個 UserStatus → 直接確認
 * 3. 若該 Group 對應多個 UserStatus → 顯示子選項（最多 3 個）
 *
 * @param emoji       UI 顯示的視覺符號
 * @param members     此大類包含的 UserStatus 列表（若只有一個則直接選中）
 * @param directStatus 若此大類唯一對應一個 UserStatus（不需子選項）
 */
enum class UserStatusGroup(
    val emoji: String,
    val members: List<UserStatus>,
    val directStatus: UserStatus? = null,
) {
    /** 規律週期，無已知影響情況 */
    REGULAR(
        emoji = "✦",
        members = listOf(UserStatus.REGULAR),
        directStatus = UserStatus.REGULAR,
    ),

    /**
     * 荷爾蒙失調（PCOS、甲狀腺疾病、高泌乳激素血症等）
     * 子選項：PCOS / 甲狀腺疾病 / 其他荷爾蒙失調
     */
    HORMONAL(
        emoji = "✧",
        members = listOf(UserStatus.PCOS, UserStatus.THYROID_DISORDER, UserStatus.OTHER_IRREGULAR),
    ),

    /**
     * 子宮結構問題（肌瘤、腺肌症、息肉、子宮內膜異位症等）
     * → OTHER_IRREGULAR（未來可細化）
     */
    STRUCTURAL(
        emoji = "◫",
        members = listOf(UserStatus.ENDOMETRIOSIS, UserStatus.OTHER_IRREGULAR),
        directStatus = UserStatus.ENDOMETRIOSIS,
    ),

    /**
     * 荷爾蒙避孕（避孕藥、IUD、植入劑等）
     */
    CONTRACEPTION(
        emoji = "◎",
        members = listOf(UserStatus.HORMONAL_CONTRACEPTION),
        directStatus = UserStatus.HORMONAL_CONTRACEPTION,
    ),

    /**
     * 人生階段（青春期、產後/哺乳期、停經過渡期）
     * 子選項：產後哺乳 / 停經過渡期
     */
    LIFE_STAGE(
        emoji = "◒",
        members = listOf(UserStatus.POSTPARTUM, UserStatus.PERIMENOPAUSE),
    ),

    /**
     * 腫瘤科治療（泰莫西芬、化療、芳香環酶抑制劑）
     * 子選項：泰莫西芬 / 其他腫瘤科治療
     */
    ONCOLOGY(
        emoji = "✜",
        members = listOf(UserStatus.ONCOLOGY_TAMOXIFEN, UserStatus.ONCOLOGY_OTHER),
    ),

    /**
     * 出血性疾病（類血友病 vWD、血小板異常、抗凝血劑使用）
     * → OTHER_IRREGULAR（保守分類）
     */
    BLEEDING_DISORDER(
        emoji = "◈",
        members = listOf(UserStatus.OTHER_IRREGULAR),
        directStatus = UserStatus.OTHER_IRREGULAR,
    ),

    /**
     * 自體免疫 / 慢性疾病（狼瘡、類風濕性關節炎、糖尿病等）
     * → OTHER_IRREGULAR（保守分類）
     */
    AUTOIMMUNE(
        emoji = "◉",
        members = listOf(UserStatus.OTHER_IRREGULAR),
        directStatus = UserStatus.OTHER_IRREGULAR,
    ),

    /**
     * 其他 / 尚不清楚
     * → OTHER_IRREGULAR
     */
    OTHER(
        emoji = "◇",
        members = listOf(UserStatus.OTHER_IRREGULAR),
        directStatus = UserStatus.OTHER_IRREGULAR,
    );

    /** 此 Group 是否只有單一 UserStatus（不需子選項步驟） */
    val isSingleStatus: Boolean get() = directStatus != null
}
