import SwiftUI
#if os(iOS)
import AVFoundation
import Photos
import UIKit
#endif

struct ReadingParkView: View {
    @EnvironmentObject var appState: AppState
    @State private var selectedBanner = 0
    @State private var showCapture = false
    @State private var showReview = false
    @State private var showWeeklyReport = false
    @State private var showAllAchievements = false
    @State private var selectedAchievement: ReadingAchievement?
    @State private var childSelectionAlertMessage: String?
    @State private var permissionAlertMessage: String?
    @State private var featureGridWidth: CGFloat = AppLayout.readableMaxWidth

    private var banners: [(icon: String, title: String, description: String)] {
        [
            (
                "🎪",
                appState.uiText("欢迎来到伴读乐园", "Welcome to Learning Park"),
                appState.uiText("探索各种有趣的伴读功能，让孩子的阅读之旅更加丰富多彩", "Explore fun reading features that make your child's reading journey richer")
            ),
            (
                "📚",
                appState.uiText("开启阅读之旅", "Start the reading journey"),
                appState.uiText("通过有趣的伴读方式，培养孩子的阅读兴趣和习惯", "Build reading interest and habits through playful read-along moments")
            ),
            (
                "🌟",
                appState.uiText("快乐学习成长", "Grow with joyful learning"),
                appState.uiText("在游戏中学习，在阅读中成长，让孩子爱上阅读", "Learn through playful practice and grow through reading")
            )
        ]
    }

    private var features: [ReadingParkFeature] {
        [
            ReadingParkFeature(
                    icon: "📷",
                    title: appState.uiText("拍拍识图", "Capture & Recognize"),
                    description: appState.uiText("拍摄文字，智能识别，一键开始学习", "Capture text, recognize it intelligently, and start learning"),
                    buttonTitle: appState.uiText("开始使用", "Start"),
                    isComingSoon: false,
                    action: validateAndOpenCapture
                ),
                ReadingParkFeature(
                    icon: "✨",
                    title: appState.uiText("伴读复习", "Review Practice"),
                    description: appState.uiText("智能安排复习计划，巩固学习成果", "Review cards on a gentle schedule to reinforce learning"),
                    buttonTitle: appState.uiText("开始复习", "Review"),
                    isComingSoon: false,
                    action: { showReview = true }
                ),
            ReadingParkFeature(
                icon: "📊",
                title: appState.uiText("阅读周报", "Weekly Reading Report"),
                description: appState.uiText("每周回顾伴读节奏和阅读成果", "Review weekly read-along rhythm and progress"),
                buttonTitle: appState.uiText("查看周报", "View report"),
                isComingSoon: false,
                action: { showWeeklyReport = true }
            ),
            ReadingParkFeature(
                icon: "📝",
                title: appState.uiText("故事创作", "Story Creation"),
                description: appState.uiText("引导孩子发挥想象力，创作属于自己的故事", "Guide children to imagine and create their own stories"),
                buttonTitle: appState.uiText("即将上线", "Coming soon"),
                isComingSoon: true,
                action: {}
            ),
            ReadingParkFeature(
                icon: "📚",
                title: appState.uiText("词汇积累", "Vocabulary"),
                description: appState.uiText("自动收集生词，建立个人词汇库", "Collect new words and build a personal vocabulary bank"),
                buttonTitle: appState.uiText("即将上线", "Coming soon"),
                isComingSoon: true,
                action: {}
            ),
            ReadingParkFeature(
                icon: "🎮",
                title: appState.uiText("互动游戏", "Interactive Games"),
                description: appState.uiText("通过游戏方式巩固阅读内容", "Use playful interactions to reinforce reading content"),
                buttonTitle: appState.uiText("即将上线", "Coming soon"),
                isComingSoon: true,
                action: {}
            )
        ]
    }

    private var achievements: [ReadingAchievement] {
        let stats = appState.readingAchievementStats
        return [
            achievement(
                id: "learning-1",
                sortOrder: 1,
                difficulty: .easy,
                icon: "🌱",
                titleZh: "第一次点亮",
                titleEn: "First spark",
                ruleZh: "完成第一张句卡学习即可点亮。",
                ruleEn: "Complete your first reading card to unlock this achievement.",
                metricZh: "次句卡学习",
                metricEn: "card learning events",
                current: stats.learningEventCount,
                target: 1
            ),
            achievement(
                id: "save-1",
                sortOrder: 2,
                difficulty: .easy,
                icon: "✨",
                titleZh: "第一次收藏",
                titleEn: "First save",
                ruleZh: "保存第一张句卡，先把一句话留住。",
                ruleEn: "Save your first reading card and keep one sentence for review.",
                metricZh: "张句卡收藏",
                metricEn: "saved cards",
                current: stats.savedCardCount,
                target: 1
            ),
            achievement(
                id: "session-2",
                sortOrder: 3,
                difficulty: .easy,
                icon: "🪜",
                titleZh: "轻练两次",
                titleEn: "Two gentle tries",
                ruleZh: "完成 2 次有效伴读或复习，先养成轻量练习节奏。",
                ruleEn: "Complete 2 effective read-along or review sessions to build a light practice rhythm.",
                metricZh: "次有效练习",
                metricEn: "effective sessions",
                current: stats.effectiveSessionCount,
                target: 2
            ),
            achievement(
                id: "streak-2",
                sortOrder: 4,
                difficulty: .easy,
                icon: "🧭",
                titleZh: "连续两天",
                titleEn: "Two-day streak",
                ruleZh: "连续 2 天保持伴读或复习，开始建立稳定节奏。",
                ruleEn: "Keep read-along or review going for 2 consecutive days to start building rhythm.",
                metricZh: "天连续",
                metricEn: "consecutive days",
                current: stats.currentStreakDays,
                target: 2
            ),
            achievement(
                id: "active-2",
                sortOrder: 5,
                difficulty: .easy,
                icon: "📆",
                titleZh: "两日活跃",
                titleEn: "Two active days",
                ruleZh: "在 2 个不同的日子里有伴读或复习记录。",
                ruleEn: "Log read-along or review on 2 different days.",
                metricZh: "个活跃日",
                metricEn: "active days",
                current: stats.activeDayCount,
                target: 2
            ),
            achievement(
                id: "save-3",
                sortOrder: 6,
                difficulty: .easy,
                icon: "📚",
                titleZh: "三句收纳",
                titleEn: "Three-card set",
                ruleZh: "累计保存 3 张句卡，让复习内容更丰富。",
                ruleEn: "Save 3 reading cards to enrich the review set.",
                metricZh: "张句卡收藏",
                metricEn: "saved cards",
                current: stats.savedCardCount,
                target: 3
            ),
            achievement(
                id: "learning-5",
                sortOrder: 7,
                difficulty: .gentle,
                icon: "🌿",
                titleZh: "五次学习",
                titleEn: "Five learning steps",
                ruleZh: "完成 5 次句卡学习，让孩子先接触足够多的句子。",
                ruleEn: "Complete 5 card learning events so the child sees enough sample sentences.",
                metricZh: "次句卡学习",
                metricEn: "card learning events",
                current: stats.learningEventCount,
                target: 5
            ),
            achievement(
                id: "save-5",
                sortOrder: 8,
                difficulty: .gentle,
                icon: "🧺",
                titleZh: "五张收藏",
                titleEn: "Five saved cards",
                ruleZh: "保存 5 张句卡，开始形成可复习的素材库。",
                ruleEn: "Save 5 cards to begin building a reviewable collection.",
                metricZh: "张句卡收藏",
                metricEn: "saved cards",
                current: stats.savedCardCount,
                target: 5
            ),
            achievement(
                id: "streak-3",
                sortOrder: 9,
                difficulty: .gentle,
                icon: "🔥",
                titleZh: "三日不断",
                titleEn: "Three-day streak",
                ruleZh: "连续 3 天伴读或复习，节奏开始成形。",
                ruleEn: "Complete read-along or review for 3 consecutive days to form the rhythm.",
                metricZh: "天连续",
                metricEn: "consecutive days",
                current: stats.currentStreakDays,
                target: 3
            ),
            achievement(
                id: "session-5",
                sortOrder: 10,
                difficulty: .gentle,
                icon: "🎵",
                titleZh: "五次练习",
                titleEn: "Five practice rounds",
                ruleZh: "完成 5 次有效伴读或复习，让练习频率更稳定。",
                ruleEn: "Complete 5 effective sessions to keep practice frequency steady.",
                metricZh: "次有效练习",
                metricEn: "effective sessions",
                current: stats.effectiveSessionCount,
                target: 5
            ),
            achievement(
                id: "active-4",
                sortOrder: 11,
                difficulty: .gentle,
                icon: "🗓️",
                titleZh: "四日活跃",
                titleEn: "Four active days",
                ruleZh: "在 4 个不同的日子里完成伴读或复习。",
                ruleEn: "Log read-along or review on 4 different days.",
                metricZh: "个活跃日",
                metricEn: "active days",
                current: stats.activeDayCount,
                target: 4
            ),
            achievement(
                id: "mastered-2",
                sortOrder: 12,
                difficulty: .gentle,
                icon: "⭐",
                titleZh: "两张熟练",
                titleEn: "Two mastered cards",
                ruleZh: "将 2 张句卡复习到熟练掌握。",
                ruleEn: "Review 2 cards until they are marked as mastered.",
                metricZh: "张熟练句卡",
                metricEn: "mastered cards",
                current: stats.masteredCardCount,
                target: 2
            ),
            achievement(
                id: "learning-10",
                sortOrder: 13,
                difficulty: .steady,
                icon: "🌾",
                titleZh: "十次学习",
                titleEn: "Ten learning steps",
                ruleZh: "完成 10 次句卡学习，逐步把阅读变成固定动作。",
                ruleEn: "Complete 10 card learning events to make reading a regular habit.",
                metricZh: "次句卡学习",
                metricEn: "card learning events",
                current: stats.learningEventCount,
                target: 10
            ),
            achievement(
                id: "save-8",
                sortOrder: 14,
                difficulty: .steady,
                icon: "📖",
                titleZh: "八句沉淀",
                titleEn: "Eight-sentence bank",
                ruleZh: "保存 8 张句卡，让复习池更完整。",
                ruleEn: "Save 8 cards to make the review pool richer.",
                metricZh: "张句卡收藏",
                metricEn: "saved cards",
                current: stats.savedCardCount,
                target: 8
            ),
            achievement(
                id: "streak-5",
                sortOrder: 15,
                difficulty: .steady,
                icon: "🏕️",
                titleZh: "五日坚持",
                titleEn: "Five-day rhythm",
                ruleZh: "连续 5 天伴读或复习，把坚持变成习惯。",
                ruleEn: "Keep read-along or review going for 5 consecutive days to turn it into a habit.",
                metricZh: "天连续",
                metricEn: "consecutive days",
                current: stats.currentStreakDays,
                target: 5
            ),
            achievement(
                id: "session-10",
                sortOrder: 16,
                difficulty: .steady,
                icon: "🎯",
                titleZh: "十次练习",
                titleEn: "Ten practice rounds",
                ruleZh: "完成 10 次有效伴读或复习，练习频率继续抬升。",
                ruleEn: "Complete 10 effective sessions to keep the practice pace rising.",
                metricZh: "次有效练习",
                metricEn: "effective sessions",
                current: stats.effectiveSessionCount,
                target: 10
            ),
            achievement(
                id: "active-7",
                sortOrder: 17,
                difficulty: .steady,
                icon: "🧩",
                titleZh: "七日活跃",
                titleEn: "Seven active days",
                ruleZh: "在 7 个不同的日子里完成伴读或复习，节奏开始稳定。",
                ruleEn: "Log read-along or review on 7 different days to stabilize the rhythm.",
                metricZh: "个活跃日",
                metricEn: "active days",
                current: stats.activeDayCount,
                target: 7
            ),
            achievement(
                id: "mastered-5",
                sortOrder: 18,
                difficulty: .steady,
                icon: "🏅",
                titleZh: "五张熟练",
                titleEn: "Five mastered cards",
                ruleZh: "将 5 张句卡复习到熟练掌握，巩固基础内容。",
                ruleEn: "Review 5 cards until they are mastered and reinforce the basics.",
                metricZh: "张熟练句卡",
                metricEn: "mastered cards",
                current: stats.masteredCardCount,
                target: 5
            ),
            achievement(
                id: "learning-15",
                sortOrder: 19,
                difficulty: .strong,
                icon: "🌻",
                titleZh: "十五次学习",
                titleEn: "Fifteen learning steps",
                ruleZh: "完成 15 次句卡学习，孩子会开始熟悉伴读流程。",
                ruleEn: "Complete 15 card learning events so the child becomes familiar with the flow.",
                metricZh: "次句卡学习",
                metricEn: "card learning events",
                current: stats.learningEventCount,
                target: 15
            ),
            achievement(
                id: "save-12",
                sortOrder: 20,
                difficulty: .strong,
                icon: "🗃️",
                titleZh: "十二句收藏",
                titleEn: "Twelve-card shelf",
                ruleZh: "保存 12 张句卡，形成更完整的复习素材。",
                ruleEn: "Save 12 cards to build a more complete review shelf.",
                metricZh: "张句卡收藏",
                metricEn: "saved cards",
                current: stats.savedCardCount,
                target: 12
            ),
            achievement(
                id: "streak-7",
                sortOrder: 21,
                difficulty: .strong,
                icon: "⏳",
                titleZh: "七日连贯",
                titleEn: "Seven-day continuity",
                ruleZh: "连续 7 天伴读或复习，让节奏真正连贯起来。",
                ruleEn: "Complete read-along or review for 7 consecutive days to keep the rhythm continuous.",
                metricZh: "天连续",
                metricEn: "consecutive days",
                current: stats.currentStreakDays,
                target: 7
            ),
            achievement(
                id: "session-20",
                sortOrder: 22,
                difficulty: .strong,
                icon: "🚀",
                titleZh: "二十次练习",
                titleEn: "Twenty practice rounds",
                ruleZh: "完成 20 次有效伴读或复习，进入稳定训练阶段。",
                ruleEn: "Complete 20 effective sessions to move into a steady training stage.",
                metricZh: "次有效练习",
                metricEn: "effective sessions",
                current: stats.effectiveSessionCount,
                target: 20
            ),
            achievement(
                id: "active-10",
                sortOrder: 23,
                difficulty: .strong,
                icon: "🧭",
                titleZh: "十日活跃",
                titleEn: "Ten active days",
                ruleZh: "在 10 个不同的日子里完成伴读或复习。",
                ruleEn: "Log read-along or review on 10 different days.",
                metricZh: "个活跃日",
                metricEn: "active days",
                current: stats.activeDayCount,
                target: 10
            ),
            achievement(
                id: "mastered-8",
                sortOrder: 24,
                difficulty: .strong,
                icon: "🏆",
                titleZh: "八张熟练",
                titleEn: "Eight mastered cards",
                ruleZh: "将 8 张句卡复习到熟练掌握，开始进入巩固期。",
                ruleEn: "Review 8 cards until they are mastered and enter the consolidation stage.",
                metricZh: "张熟练句卡",
                metricEn: "mastered cards",
                current: stats.masteredCardCount,
                target: 8
            ),
            achievement(
                id: "learning-20",
                sortOrder: 25,
                difficulty: .advanced,
                icon: "🌟",
                titleZh: "二十次学习",
                titleEn: "Twenty learning steps",
                ruleZh: "完成 20 次句卡学习，陪读习惯已经有了明显积累。",
                ruleEn: "Complete 20 card learning events and the read-along habit will be clearly established.",
                metricZh: "次句卡学习",
                metricEn: "card learning events",
                current: stats.learningEventCount,
                target: 20
            ),
            achievement(
                id: "save-15",
                sortOrder: 26,
                difficulty: .advanced,
                icon: "🪴",
                titleZh: "十五句收藏",
                titleEn: "Fifteen-card bank",
                ruleZh: "保存 15 张句卡，让复习资源足够覆盖更多场景。",
                ruleEn: "Save 15 cards so the review set can cover more situations.",
                metricZh: "张句卡收藏",
                metricEn: "saved cards",
                current: stats.savedCardCount,
                target: 15
            ),
            achievement(
                id: "streak-14",
                sortOrder: 27,
                difficulty: .advanced,
                icon: "🌈",
                titleZh: "十四日陪伴",
                titleEn: "Fourteen-day companion",
                ruleZh: "连续 14 天伴读或复习，把陪伴变成长期节奏。",
                ruleEn: "Keep read-along or review going for 14 consecutive days and turn it into a long-term rhythm.",
                metricZh: "天连续",
                metricEn: "consecutive days",
                current: stats.currentStreakDays,
                target: 14
            ),
            achievement(
                id: "session-30",
                sortOrder: 28,
                difficulty: .advanced,
                icon: "🎖️",
                titleZh: "三十次练习",
                titleEn: "Thirty practice rounds",
                ruleZh: "完成 30 次有效伴读或复习，说明练习已经持续展开。",
                ruleEn: "Complete 30 effective sessions to show the practice cycle is well underway.",
                metricZh: "次有效练习",
                metricEn: "effective sessions",
                current: stats.effectiveSessionCount,
                target: 30
            ),
            achievement(
                id: "active-14",
                sortOrder: 29,
                difficulty: .advanced,
                icon: "📅",
                titleZh: "十四日活跃",
                titleEn: "Fourteen active days",
                ruleZh: "在 14 个不同的日子里完成伴读或复习。",
                ruleEn: "Log read-along or review on 14 different days.",
                metricZh: "个活跃日",
                metricEn: "active days",
                current: stats.activeDayCount,
                target: 14
            ),
            achievement(
                id: "mastered-10",
                sortOrder: 30,
                difficulty: .advanced,
                icon: "👑",
                titleZh: "十张熟练",
                titleEn: "Ten mastered cards",
                ruleZh: "将 10 张句卡复习到熟练掌握，进入长期稳定阶段。",
                ruleEn: "Review 10 cards until they are mastered and step into a stable long-term stage.",
                metricZh: "张熟练句卡",
                metricEn: "mastered cards",
                current: stats.masteredCardCount,
                target: 10
            ),
            achievement(
                id: "learning-30",
                sortOrder: 31,
                difficulty: .advanced,
                icon: "🌠",
                titleZh: "三十次学习",
                titleEn: "Thirty learning steps",
                ruleZh: "完成 30 次句卡学习，让伴读流程真正熟悉起来。",
                ruleEn: "Complete 30 card learning events so the read-along flow becomes truly familiar.",
                metricZh: "次句卡学习",
                metricEn: "card learning events",
                current: stats.learningEventCount,
                target: 30
            ),
            achievement(
                id: "save-20",
                sortOrder: 32,
                difficulty: .advanced,
                icon: "📦",
                titleZh: "二十句收藏",
                titleEn: "Twenty-card collection",
                ruleZh: "保存 20 张句卡，形成稳定可用的家庭复习素材。",
                ruleEn: "Save 20 cards to build a stable family review collection.",
                metricZh: "张句卡收藏",
                metricEn: "saved cards",
                current: stats.savedCardCount,
                target: 20
            ),
            achievement(
                id: "streak-21",
                sortOrder: 33,
                difficulty: .advanced,
                icon: "🛤️",
                titleZh: "二十一日同行",
                titleEn: "Twenty-one-day journey",
                ruleZh: "连续 21 天伴读或复习，让亲子陪读进入长期节奏。",
                ruleEn: "Keep read-along or review going for 21 consecutive days to make it a long-term family rhythm.",
                metricZh: "天连续",
                metricEn: "consecutive days",
                current: stats.currentStreakDays,
                target: 21
            ),
            achievement(
                id: "session-45",
                sortOrder: 34,
                difficulty: .advanced,
                icon: "🏃",
                titleZh: "四十五次练习",
                titleEn: "Forty-five practice rounds",
                ruleZh: "完成 45 次有效伴读或复习，保持持续训练状态。",
                ruleEn: "Complete 45 effective sessions and keep the practice cycle active.",
                metricZh: "次有效练习",
                metricEn: "effective sessions",
                current: stats.effectiveSessionCount,
                target: 45
            ),
            achievement(
                id: "active-21",
                sortOrder: 35,
                difficulty: .advanced,
                icon: "🗒️",
                titleZh: "二十一日活跃",
                titleEn: "Twenty-one active days",
                ruleZh: "在 21 个不同的日子里完成伴读或复习。",
                ruleEn: "Log read-along or review on 21 different days.",
                metricZh: "个活跃日",
                metricEn: "active days",
                current: stats.activeDayCount,
                target: 21
            ),
            achievement(
                id: "mastered-15",
                sortOrder: 36,
                difficulty: .advanced,
                icon: "💎",
                titleZh: "十五张熟练",
                titleEn: "Fifteen mastered cards",
                ruleZh: "将 15 张句卡复习到熟练掌握，巩固更多常用表达。",
                ruleEn: "Review 15 cards until they are mastered and reinforce more common expressions.",
                metricZh: "张熟练句卡",
                metricEn: "mastered cards",
                current: stats.masteredCardCount,
                target: 15
            ),
            achievement(
                id: "learning-40",
                sortOrder: 37,
                difficulty: .advanced,
                icon: "🌌",
                titleZh: "四十次学习",
                titleEn: "Forty learning steps",
                ruleZh: "完成 40 次句卡学习，让孩子积累更宽的阅读接触面。",
                ruleEn: "Complete 40 card learning events to give the child a broader reading base.",
                metricZh: "次句卡学习",
                metricEn: "card learning events",
                current: stats.learningEventCount,
                target: 40
            ),
            achievement(
                id: "save-25",
                sortOrder: 38,
                difficulty: .advanced,
                icon: "🏛️",
                titleZh: "二十五句书架",
                titleEn: "Twenty-five-card shelf",
                ruleZh: "保存 25 张句卡，让复习素材覆盖更多阅读场景。",
                ruleEn: "Save 25 cards so the review shelf covers more reading situations.",
                metricZh: "张句卡收藏",
                metricEn: "saved cards",
                current: stats.savedCardCount,
                target: 25
            ),
            achievement(
                id: "streak-30",
                sortOrder: 39,
                difficulty: .advanced,
                icon: "🏔️",
                titleZh: "三十日坚持",
                titleEn: "Thirty-day streak",
                ruleZh: "连续 30 天伴读或复习，形成稳定的家庭伴读习惯。",
                ruleEn: "Keep read-along or review going for 30 consecutive days to form a stable family habit.",
                metricZh: "天连续",
                metricEn: "consecutive days",
                current: stats.currentStreakDays,
                target: 30
            ),
            achievement(
                id: "session-60",
                sortOrder: 40,
                difficulty: .advanced,
                icon: "🏁",
                titleZh: "六十次练习",
                titleEn: "Sixty practice rounds",
                ruleZh: "完成 60 次有效伴读或复习，代表长期陪读已经持续展开。",
                ruleEn: "Complete 60 effective sessions, showing that long-term read-along practice is underway.",
                metricZh: "次有效练习",
                metricEn: "effective sessions",
                current: stats.effectiveSessionCount,
                target: 60
            )
        ].sorted {
            if $0.difficulty.rawValue == $1.difficulty.rawValue {
                return $0.sortOrder < $1.sortOrder
            }
            return $0.difficulty.rawValue < $1.difficulty.rawValue
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: AppLayout.spacingXL) {
                    bannerCarousel
                    featureSection
                    achievementSection
                    tipsSection
                }
                .padding(.horizontal, AppLayout.paddingScreen)
                .padding(.top, 0)
                .padding(.bottom, AppLayout.spacingL)
                .adaptiveContentFrame(maxWidth: AppLayout.wideReadableMaxWidth)
            }
            .background(AppColors.background)
            .toolbar(.hidden, for: .navigationBar)
            .toolbarBackground(.hidden, for: .navigationBar)
            .fullScreenCover(isPresented: $showCapture) {
                CaptureView(shouldRequestInitialPermissions: true)
                    .environmentObject(appState)
            }
            .navigationDestination(isPresented: $showReview) {
                ReviewView(showsAllLearningCards: true)
                    .environmentObject(appState)
            }
            .navigationDestination(isPresented: $showWeeklyReport) {
                WeeklyReportView()
                    .environmentObject(appState)
            }
            .sheet(isPresented: $showAllAchievements) {
                AchievementListView(achievements: achievements)
                    .environmentObject(appState)
            }
            .alert(appState.uiText("无法进入拍拍识图", "Cannot start Capture & Recognize"), isPresented: Binding(get: { childSelectionAlertMessage != nil }, set: { if !$0 { childSelectionAlertMessage = nil } })) {
                Button(appState.uiText("知道了", "Got it")) {
                    childSelectionAlertMessage = nil
                }
            } message: {
                Text(childSelectionAlertMessage ?? "")
            }
            .alert(appState.uiText("需要开启权限", "Permissions required"), isPresented: Binding(get: { permissionAlertMessage != nil }, set: { if !$0 { permissionAlertMessage = nil } })) {
                #if os(iOS)
                Button(appState.uiText("前往设置", "Open Settings")) {
                    openSystemSettings()
                }
                #endif
                Button(appState.uiText("知道了", "Got it"), role: .cancel) {
                    permissionAlertMessage = nil
                }
            } message: {
                Text(permissionAlertMessage ?? "")
            }
            .alert(item: $selectedAchievement) { achievement in
                Alert(
                    title: Text(achievement.icon + " " + achievement.title),
                    message: Text(achievement.rule + "\n" + achievement.progress),
                    dismissButton: .default(Text(appState.uiText("知道了", "Got it")))
                )
            }
            .task {
                await appState.bootstrapIfNeeded()
                await appState.refreshParentData()
                await appState.refreshReviewData()
                await appState.refreshReadingAchievementStats()
            }
            .onChange(of: appState.selectedChild.id) { _, _ in
                Task {
                    await appState.refreshReviewData()
                    await appState.refreshReadingAchievementStats()
                }
            }
        }
    }

    private var bannerCarousel: some View {
        GeometryReader { proxy in
            let bannerHeight = proxy.size.width * 9 / 16

            ZStack(alignment: .bottom) {
                TabView(selection: $selectedBanner) {
                    ForEach(Array(banners.enumerated()), id: \.offset) { index, banner in
                        VStack(spacing: AppLayout.spacingS) {
                            Text(banner.icon)
                                .font(AppTypography.scaledFont(size: min(max(proxy.size.width * 0.12, 34), 54)))
                                .frame(width: min(max(proxy.size.width * 0.18, 58), 88), height: min(max(proxy.size.width * 0.18, 58), 88))
                                .background(Color.white.opacity(0.2))
                                .clipShape(Circle())
                            Text(banner.title)
                                .font(AppTypography.title3)
                                .foregroundColor(.white)
                                .lineLimit(1)
                                .minimumScaleFactor(0.75)
                            Text(banner.description)
                                .font(AppTypography.footnote)
                                .foregroundColor(.white.opacity(0.92))
                                .multilineTextAlignment(.center)
                                .lineSpacing(2)
                                .lineLimit(2)
                                .minimumScaleFactor(0.85)
                        }
                        .padding(.horizontal, AppLayout.spacingL)
                        .padding(.vertical, AppLayout.spacingM)
                        .frame(width: proxy.size.width, height: bannerHeight)
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                HStack(spacing: AppLayout.spacingS) {
                    ForEach(banners.indices, id: \.self) { index in
                        Capsule()
                            .fill(index == selectedBanner ? Color.white : Color.white.opacity(0.45))
                            .frame(width: index == selectedBanner ? 16 : 6, height: 6)
                            .animation(.easeInOut(duration: 0.25), value: selectedBanner)
                    }
                }
                .padding(.horizontal, AppLayout.spacingM)
                .padding(.vertical, 6)
                .background(Color.black.opacity(0.12))
                .clipShape(Capsule())
                .padding(.bottom, 6)
            }
            .frame(width: proxy.size.width, height: bannerHeight)
            .background(AppGradients.primary)
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXXL, style: .continuous))
            .shadow(color: Color.black.opacity(0.15), radius: 16, x: 0, y: 4)
        }
        .aspectRatio(16 / 9, contentMode: .fit)
    }

    private var featureSection: some View {
        VStack(alignment: .leading, spacing: AppLayout.spacingS) {
            Text(appState.uiText("核心功能", "Core features"))
                .font(AppTypography.headline)
                .foregroundColor(AppColors.textPrimary)
            LazyVGrid(columns: featureGridColumns, spacing: featureGridSpacing) {
                ForEach(features) { feature in
                    ReadingParkFeatureCard(
                        feature: feature,
                        isSingleColumn: featureGridColumnCount == 1
                    )
                }
            }
            .background(
                GeometryReader { proxy in
                    Color.clear.preference(key: FeatureGridWidthPreferenceKey.self, value: proxy.size.width)
                }
            )
            .onPreferenceChange(FeatureGridWidthPreferenceKey.self) { width in
                guard width > 0 else { return }
                featureGridWidth = width
            }
        }
    }

    private var featureGridColumns: [GridItem] {
        Array(
            repeating: GridItem(.flexible(minimum: 0), spacing: featureGridSpacing),
            count: featureGridColumnCount
        )
    }

    private var featureGridColumnCount: Int {
        featureGridWidth < 728 ? 1 : 2
    }

    private var featureGridSpacing: CGFloat {
        featureGridColumnCount == 1 ? AppLayout.spacingM : AppLayout.spacingS
    }

    private var achievementSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                HStack {
                    Text(appState.uiText("成就", "Achievements"))
                        .font(AppTypography.headline)
                        .foregroundColor(AppColors.textPrimary)
                    Spacer()
                    Button {
                        showAllAchievements = true
                    } label: {
                        Text(appState.uiText("更多", "More"))
                            .font(AppTypography.caption)
                            .foregroundColor(AppColors.primary)
                    }
                    .buttonStyle(.plain)
                }

                HStack(spacing: AppLayout.spacingS) {
                    ForEach(Array(achievements.prefix(4))) { achievement in
                        AchievementBadge(achievement: achievement) {
                            selectedAchievement = achievement
                        }
                    }
                }
            }
        }
    }

    private var tipsSection: some View {
        MainCard {
            VStack(alignment: .leading, spacing: AppLayout.spacingM) {
                Text(appState.uiText("伴读小贴士", "Read-along tips"))
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)
                tipItem(icon: "💡", text: appState.uiText("每天固定时间伴读，培养良好的阅读习惯", "Read together at a regular time each day to build a reading habit"))
                tipItem(icon: "📖", text: appState.uiText("选择适合孩子年龄段的阅读材料", "Choose reading materials that fit your child's age and interests"))
                tipItem(icon: "👨‍🏫", text: appState.uiText("家长参与互动，分享阅读心得", "Join the interaction and share reading moments with your child"))
            }
        }
    }

    private func tipItem(icon: String, text: String) -> some View {
        HStack(alignment: .top, spacing: AppLayout.spacingM) {
            Text(icon)
                .font(AppTypography.scaledFont(size: 18))
                .padding(.top, 1)
            Text(text)
                .font(AppTypography.footnote)
                .foregroundColor(AppColors.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func validateAndOpenCapture() {
        Task { await validatePermissionsAndOpenCapture() }
    }

    @MainActor
    private func validatePermissionsAndOpenCapture() async {
        await appState.refreshParentData()
        guard !appState.children.isEmpty else {
            childSelectionAlertMessage = appState.uiText("请先在家长中心添加至少一名孩子档案，再进入拍拍识图。", "Please add at least one child profile in Parent Center before starting Capture & Recognize.")
            return
        }
        guard appState.children.contains(where: { $0.id == appState.selectedChild.id && !$0.isDeleted }) else {
            childSelectionAlertMessage = appState.uiText("请先在首页选择一个具体的孩子，再进入拍拍识图。", "Please select a child on Home before starting Capture & Recognize.")
            return
        }
        guard await ensureCapturePermissions() else { return }
        showCapture = true
    }

    @MainActor
    private func ensureCapturePermissions() async -> Bool {
        #if os(iOS)
        #if targetEnvironment(simulator)
        return await ensurePhotoLibraryPermission()
        #else
        guard await ensureCameraPermission() else { return false }
        return await ensurePhotoLibraryPermission()
        #endif
        #else
        return true
        #endif
    }

    #if os(iOS)
    @MainActor
    private func ensureCameraPermission() async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            return true
        case .notDetermined:
            return await withCheckedContinuation { continuation in
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    Task { @MainActor in
                        if !granted {
                            presentPermissionAlert(kind: .camera)
                        }
                        continuation.resume(returning: granted)
                    }
                }
            }
        default:
            presentPermissionAlert(kind: .camera)
            return false
        }
    }

    @MainActor
    private func ensurePhotoLibraryPermission() async -> Bool {
        switch PHPhotoLibrary.authorizationStatus(for: .readWrite) {
        case .authorized, .limited:
            return true
        case .notDetermined:
            return await withCheckedContinuation { continuation in
                PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
                    Task { @MainActor in
                        let granted = status == .authorized || status == .limited
                        if !granted {
                            presentPermissionAlert(kind: .photos)
                        }
                        continuation.resume(returning: granted)
                    }
                }
            }
        default:
            presentPermissionAlert(kind: .photos)
            return false
        }
    }

    private enum CaptureEntryPermissionKind {
        case camera
        case photos
    }

    @MainActor
    private func presentPermissionAlert(kind: CaptureEntryPermissionKind) {
        switch kind {
        case .camera:
            permissionAlertMessage = appState.uiText(
                "拍拍识图需要使用相机拍摄文字。请允许相机权限后再继续使用。",
                "Capture & Recognize needs camera access to photograph text. Please allow camera access to continue."
            )
        case .photos:
            permissionAlertMessage = appState.uiText(
                "拍拍识图需要访问图库以选择图片。请允许照片权限后再继续使用。",
                "Capture & Recognize needs photo library access to choose images. Please allow Photos access to continue."
            )
        }
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
    #endif
}

private struct ReadingParkFeature: Identifiable {
    let id = UUID()
    let icon: String
    let title: String
    let description: String
    let buttonTitle: String
    let isComingSoon: Bool
    let action: () -> Void
}

private struct ReadingAchievement: Identifiable, Hashable {
    let id: String
    let difficulty: AchievementDifficulty
    let sortOrder: Int
    let icon: String
    let title: String
    let rule: String
    let progress: String
    let isUnlocked: Bool
}

private enum AchievementDifficulty: Int {
    case easy = 0
    case gentle = 1
    case steady = 2
    case strong = 3
    case advanced = 4
}

private struct FeatureGridWidthPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = AppLayout.readableMaxWidth

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

private extension ReadingParkView {
    func achievement(
        id: String,
        sortOrder: Int,
        difficulty: AchievementDifficulty,
        icon: String,
        titleZh: String,
        titleEn: String,
        ruleZh: String,
        ruleEn: String,
        metricZh: String,
        metricEn: String,
        current: Int,
        target: Int
    ) -> ReadingAchievement {
        ReadingAchievement(
            id: id,
            difficulty: difficulty,
            sortOrder: sortOrder,
            icon: icon,
            title: appState.uiText(titleZh, titleEn),
            rule: appState.uiText(ruleZh, ruleEn),
            progress: appState.uiText("当前：已完成 \(current) \(metricZh)，目标 \(target) \(metricZh)。", "Current: \(current) \(metricEn) completed, goal \(target) \(metricEn)."),
            isUnlocked: current >= target
        )
    }
}

private struct ReadingParkFeatureCard: View {
    let feature: ReadingParkFeature
    let isSingleColumn: Bool

    var body: some View {
        Button(action: feature.action) {
            GeometryReader { proxy in
                let compact = proxy.size.width < 240
                let iconSize = compact ? CGFloat(44) : CGFloat(52)
                let iconFontSize = compact ? CGFloat(24) : CGFloat(28)
                let buttonPadding = compact ? AppLayout.spacingM : AppLayout.spacingL
                let buttonHeight = compact ? CGFloat(30) : CGFloat(34)

                VStack(spacing: compact ? AppLayout.spacingXS : AppLayout.spacingS) {
                    ZStack(alignment: .topTrailing) {
                        Text(feature.icon)
                            .font(AppTypography.scaledFont(size: iconFontSize))
                            .frame(width: iconSize, height: iconSize)
                            .background(feature.isComingSoon ? Color.gray.opacity(0.12) : AppColors.secondary.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
                        if feature.isComingSoon {
                            Text(feature.buttonTitle)
                                .font(AppTypography.scaledFont(size: 8, weight: .semibold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(AppColors.accentYellow)
                                .clipShape(Capsule())
                                .offset(x: 12, y: -6)
                        }
                    }

                    VStack(spacing: 2) {
                        Text(feature.title)
                            .font(AppTypography.scaledFont(size: compact ? 13 : 14, weight: .semibold))
                            .foregroundColor(AppColors.textPrimary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Text(feature.description)
                            .font(AppTypography.scaledFont(size: compact ? 11 : 12))
                            .foregroundColor(AppColors.textSecondary)
                            .multilineTextAlignment(.center)
                            .lineLimit(isSingleColumn ? 2 : 3)
                            .minimumScaleFactor(0.75)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxHeight: .infinity)

                    Spacer(minLength: 0)

                    Text(feature.buttonTitle)
                        .font(AppTypography.scaledFont(size: compact ? 12 : 13, weight: .semibold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .frame(height: buttonHeight)
                        .padding(.horizontal, buttonPadding)
                        .background {
                            if feature.isComingSoon {
                                AppColors.border
                            } else {
                                AppGradients.primary
                            }
                        }
                        .clipShape(Capsule())
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.vertical, compact ? AppLayout.spacingM : AppLayout.spacingL)
                .padding(.horizontal, compact ? AppLayout.spacingM : AppLayout.spacingL)
            }
            .frame(height: isSingleColumn ? 176 : 188)
            .background(Color.white.opacity(feature.isComingSoon ? 0.72 : 1))
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 12, x: 0, y: 2)
            .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusXL, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(feature.isComingSoon)
    }
}

private struct AchievementBadge: View {
    let achievement: ReadingAchievement
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text(achievement.icon)
                    .font(AppTypography.scaledFont(size: 26))
                    .opacity(achievement.isUnlocked ? 1 : 0.35)
                Text(achievement.title)
                    .font(AppTypography.scaledFont(size: 10, weight: .medium))
                    .foregroundColor(achievement.isUnlocked ? AppColors.textPrimary : AppColors.textTertiary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.75)
            }
            .frame(maxWidth: .infinity, minHeight: 72)
            .padding(.vertical, AppLayout.spacingS)
            .background(achievement.isUnlocked ? AppColors.primary.opacity(0.08) : Color.gray.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: AppLayout.cornerRadiusM, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct AchievementListView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var appState: AppState
    let achievements: [ReadingAchievement]
    @State private var selectedAchievement: ReadingAchievement?

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: AppLayout.spacingM)], spacing: AppLayout.spacingM) {
                    ForEach(achievements) { achievement in
                        AchievementBadge(achievement: achievement) {
                            selectedAchievement = achievement
                        }
                    }
                }
                .padding(AppLayout.paddingScreen)
            }
            .background(AppColors.background)
            .navigationTitle(appState.uiText("全部成就", "All achievements"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(appState.uiText("关闭", "Close")) {
                        dismiss()
                    }
                }
            }
            .alert(item: $selectedAchievement) { achievement in
                Alert(
                    title: Text(achievement.icon + " " + achievement.title),
                    message: Text(achievement.rule + "\n" + achievement.progress),
                    dismissButton: .default(Text(appState.uiText("知道了", "Got it")))
                )
            }
        }
    }
}

#Preview {
    ReadingParkView()
}
