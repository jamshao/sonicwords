# SonicWords

## 项目概述
SonicWords 是一个基于 Android 的单词学习应用，采用 MVVM 架构，使用 Room 进行本地数据存储。

## 功能特性

### 语音识别学习功能

#### 功能描述
SonicWords 实现了基于语音识别的单词学习功能，用户需要通过语音输入来拼写每个单词。系统会逐个字母提示用户，并判断用户的发音是否正确。

#### 实现细节

1. **语音识别实现**
   - 使用 Vosk 离线语音识别引擎，支持无网络环境下的语音识别
   - 通过按钮按下触发识别，松开按钮结束识别，提供更精确的控制
   - 支持字母级别的逐字母拼写和识别
   - 配置为优化识别英文字母的模式

2. **字母级别拼写功能**
   - 实现单词按字母拼写的学习模式，提高拼写记忆效果
   - 系统提示当前需要拼写的单词和中文翻译，然后等待用户语音输入该单词的所有字母。
   - 用户也可以选择键盘输入单词的每个字母。系统进行判断
   - 实时反馈拼写正确与否，正确则tts语音提示“真棒”自动进入下一个单词。
   - 如果拼写错误，系统tts提示用户"您的字母拼写有误"，然后按字母进行单词发音拼写。提醒用户正确的发音。
   - 拼写错误次数计数，错误超过三次将单词标记为困难

3. **语音提示系统**
   - 使用 `TextToSpeech` 提供语音提示
   - 每个字母发音提示
   - 拼写错误时提供正确发音和字母提示

4. **UI 显示**
   - 显示当前需要拼写的单词的中文释义
   - 显示当前需要拼写的字母
   - 显示用户语音输入的字母
   - 通过颜色变化提供视觉反馈（正确为绿色，错误为红色）
   - 显示学习状态和进度

5. **流程控制**
   - 单词切换时自动开始语音识别
   - 拼写完成后自动切换到下一个单词
   - 支持手动切换单词

#### 使用流程

1. 系统显示单词和意思
2. TTS 发音提示拼写
3. 用户语音输入字母
4. 系统显示用户输入的字母
5. 拼写错误时：
   - 提示错误
   - 提供正确发音
   - 自动切换到下一个单词
6. 拼写正确时：
   - 显示正确
   - 自动切换到下一个字母
   - 完成后自动切换到下一个单词

#### 技术实现

1. **依赖添加**
```gradle
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Vosk语音识别引擎
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("org.vosk:vosk-android:0.3.47")
    
    // 其他依赖...
}
```

2. **Vosk语音识别配置**
```kotlin
// 初始化Vosk识别器
private fun initializeVosk() {
    lifecycleScope.launch {
        try {
            val success = voskRecognitionService.initializeRecognizer()
            if (success) {
                Log.d(TAG, "Vosk初始化成功")
                // 配置Vosk为字母识别模式
                configureVoskForLetters()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vosk初始化异常: ${e.message}", e)
        }
    }
}

// 配置Vosk为字母识别模式
private fun configureVoskForLetters() {
    // 配置Vosk能够识别单个字母
    val alphabet = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", 
                         "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z")
    voskRecognitionService.configureVocabulary(alphabet)
}
```

3. **按钮触发语音识别**
```kotlin
// 设置录音按钮的触摸事件
private fun setupRecordButton() {
    binding.btnRecord.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 按下按钮时开始录音
                startListening()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 松开按钮时停止录音
                stopListening()
            }
        }
        true
    }
}
```

4. **字母级别拼写实现**
```kotlin
private fun processVoskResult(hypothesis: String) {
    try {
        val result = JSONObject(hypothesis)
        val text = result.optString("text").trim().lowercase(Locale.US)
        
        if (!text.isNullOrEmpty()) {
            binding.tvSpokenLetter.text = "您说: $text"
            
            // 获取当前单词和当前应拼写的字母
            val currentWord = viewModel.getCurrentWord()
            val currentLetter = viewModel.getCurrentLetter()
            
            if (currentWord != null && currentLetter != null) {
                // 判断用户是否正确拼写了当前字母
                if (viewModel.checkLetter(text)) {
                    // 字母拼写正确
                    binding.speechStatusText.text = "字母 ${currentLetter.uppercase()} 拼写正确!"
                    
                    // 移动到下一个字母
                    val isWordComplete = viewModel.nextLetter()
                    
                    if (isWordComplete) {
                        // 单词拼写完成，显示提示并准备下一个单词
                        binding.speechStatusText.text = "单词拼写完成!"
                    } else {
                        // 准备拼写下一个字母
                        val nextLetter = viewModel.getCurrentLetter()
                        if (nextLetter != null) {
                            // 更新UI显示下一个字母
                            binding.tv_current_letter.text = "请拼读字母: ${nextLetter.uppercase()}"
                            // 朗读下一个字母提示
                            ttsHelper.speak("请拼读字母 ${nextLetter.uppercase()}")
                        }
                    }
                } else {
                    // 字母拼写错误
                    binding.speechStatusText.text = "错误! 正确的字母是: ${currentLetter.uppercase()}"
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "解析结果失败: ${e.message}", e)
    }
}
```

5. **UI视觉反馈实现**
```kotlin
// 观察拼写状态变化
viewModel.spellingState.observe(viewLifecycleOwner, Observer { state ->
    when (state) {
        WordStudyViewModel.SpellingState.CorrectLetter -> {
            // 字母拼写正确的视觉反馈
            binding.tvSpokenLetter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        }
        WordStudyViewModel.SpellingState.WrongLetter -> {
            // 字母拼写错误的视觉反馈
            binding.tvSpokenLetter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        }
        WordStudyViewModel.SpellingState.CompleteWord -> {
            // 单词完成的视觉反馈
            binding.tvSpokenLetter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            binding.tvSpokenLetter.text = "单词拼写完成!"
        }
        else -> {
            // 重置文本颜色
            binding.tvSpokenLetter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        }
    }
})
```

#### 注意事项

1. 确保设备有麦克风权限
2. 保持网络连接以支持语音识别
3. 语音识别可能受到环境噪音影响
4. 建议在安静环境中使用

### 1. 单词列表展示
- **实现文件**：
  - `WordListFragment.kt`：单词列表页面的Fragment，负责UI展示和用户交互
  - `WordAdapter.kt`：RecyclerView的适配器，负责单词数据的绑定和展示
  - `WordViewModel.kt`：ViewModel组件，管理单词数据和业务逻辑
  - `menu_word_list_selection.xml`：选择模式下的菜单布局
- **功能描述**：
  - **基本展示**：使用RecyclerView展示数据库中的单词列表和对应的中文解释
  - **单词操作**：
    - 支持点击单词查看详情
    - 支持左右滑动删除单词（带撤销功能）
    - 支持长按进入批量选择模式
  - **批量操作**：
    - 支持批量选择单词（通过长按进入选择模式）
    - 支持全选功能（通过菜单）
    - 支持批量删除选中的单词（带确认对话框）
  - **空视图**：当列表为空时显示提示信息
- **技术实现**：
  - 使用RecyclerView和CardView实现单词列表的展示
  - 使用ItemTouchHelper实现滑动删除功能
  - 使用MenuProvider实现动态菜单
  - 使用ViewModel和LiveData实现数据观察和状态管理
  - 使用MaterialAlertDialogBuilder实现确认对话框
  - 使用Snackbar提供操作反馈和撤销功能

### 2. 文件导入功能
- **实现文件**：
  - `WordListFragment.kt`：实现文件选择和导入逻辑
  - `WordViewModel.kt`：处理导入的单词数据
  - `WordRepository.kt`：提供数据持久化操作
- **功能描述**：支持通过TXT/CSV格式文件批量导入单词，支持以逗号分割的多个单词或者词组。
- **技术实现**：使用MaterialAlertDialogBuilder构建文件选择对话框，通过Storage Access Framework (SAF)读取本地文件

### 3. 数据存储与管理
- **实现文件**：
  - `Word.kt`：单词实体类，定义单词的数据结构
  - `WordDao.kt`：数据访问对象，提供数据库操作接口
  - `AppDatabase.kt`：Room数据库配置
  - `WordRepository.kt`：数据仓库，封装数据操作逻辑
- **功能描述**：实现单词数据的增删改查操作
- **技术实现**：使用Room数据库存储单词数据

### 4. 依赖注入
- **实现文件**：
  - `SonicWordsApplication.kt`：应用程序入口，启用Hilt
  - `DatabaseModule.kt`：提供数据库相关依赖
- **功能描述**：实现依赖注入，简化组件间依赖关系
- **技术实现**：使用Hilt框架实现依赖注入

### 5. 导航与界面框架
- **实现文件**：
  - `MainActivity.kt`：主活动，实现导航抽屉和导航控制
  - `mobile_navigation.xml`：定义导航图和目的地
- **功能描述**：实现应用的基本导航结构
- **技术实现**：使用Navigation Component实现Fragment间导航

### 6. 单词输入功能
- **实现文件**：
  - `WordInputFragment.kt`：单词输入页面的Fragment，负责UI展示和用户交互
  - `WordViewModel.kt`：处理单词数据的添加和保存
  - `WordDao.kt`：提供数据库操作接口，包括根据单词文本查找单词
  - `WordRepository.kt`：数据仓库，封装数据操作逻辑
- **功能描述**：
  - **文本输入**：支持手动文本输入单词，格式支持：
    - 逗号分隔的多个单词
    - 空格分隔的多个单词
    - 换行分隔的多个单词
  - **OCR识别**：支持通过相机拍照识别打印或手写的单词
    - 拍照后自动识别图片中的文字
    - 识别结果显示在界面上，用户可选择是否添加到输入框
    - 自动过滤一些特殊符号
  - **语音输入**：支持通过语音识别输入单词
    - 用户可以通过语音输入单个或多个单词
    - 识别结果自动添加到输入框
  - **自动翻译**：支持自动翻译识别出的英文单词为中文
    - 调用Google ML 的本地翻译功能，不需要联网翻译
    - 翻译结果自动保存到数据库
    - 支持查找已有单词并更新释义
- **技术实现**：
  - **文本输入**：使用Material Design的TextInputLayout和TextInputEditText
  - **OCR识别**：
    - 使用Google ML Kit Text Recognition（版本16.0.0）实现文本识别
    - 使用CameraX（版本1.3.1）实现相机功能
    - 实现相机权限请求和管理
  - **语音输入**：
    - 使用Android SpeechRecognizer API实现语音识别
    - 实现麦克风权限请求和管理
    - 提供语音识别状态反馈和错误处理
  - **自动翻译**：
    - 使用Google ML Kit Translation（版本17.0.1）实现英文到中文的翻译
    - 实现翻译结果的数据库存储
  - **兼容性检查**：
    - 检查设备是否支持语音识别、OCR和翻译功能
    - 对不支持的功能进行禁用并提供用户提示

### 7. 单词学习功能
- **实现文件**：
  - `WordStudyFragment.kt`：单词学习页面的Fragment，负责UI展示和用户交互
  - `WordStudyViewModel.kt`：管理单词学习数据和业务逻辑
  - `WordCardAdapter.kt`：单词卡片适配器，负责单词卡片的展示
- **功能描述**：
  - 单词卡片展示：使用ViewPager2实现单词卡片显示单词中文意思，支持3D效果卡片左右滑动切换
  - 发音功能：集成TextToSpeech实现单词发音，根据设置中的发音方式
  - 语音识别功能： 发音结束后，tts提示（请开始拼写单词字幕）用户使用语音输入单词的每个字母，实现语音识别判断用户是否拼写正确。超过5秒没有识别到语音自动判断为拼写错误。进入下一个单词。
  - 学习进度跟踪：使用progress组件记录学习状态和进度（未掌握，不认识，已掌握）
  - 用户如果语音拼写正确，自动进入下一个单词。错误次数超过3次，标记为不认识。
  - 单词标记：支持标记单词为"已掌握"或"不认识"
- **技术实现**：
  - 使用ViewPager2和自定义动画实现单词卡片滑动效果
  - 使用TextToSpeech API实现英语发音功能
  - 使用ViewModel和LiveData管理学习状态和进度
  - 使用SpeechRecognizer API实现语音识别功能
  - 使用Snackbar提供操作反馈和错误处理

### 8. 学习算法优化
- **实现文件**：
  - `Word.kt`：扩展单词数据模型，添加熟悉度、正确次数等字段
  - `WordStudyViewModel.kt`：实现间隔重复学习算法和学习统计功能
- **功能描述**：
  - **间隔重复学习系统 (Spaced Repetition System)**：
    - 基于艾宾浩斯遗忘曲线实现的记忆算法
    - 根据单词熟悉度自动调整复习间隔
    - 熟悉度范围为0-5，答对+1，答错-1
    - 复习间隔随熟悉度增加而延长（1小时到14天不等）
  - **学习数据跟踪**：
    - 记录每个单词的正确次数、错误次数和复习次数
    - 计算单词熟悉度并安排下次复习时间
    - 识别用户的薄弱单词（错误次数大于正确次数）
  - **个性化学习设置**：
    - 支持自定义每次学习的单词数量
    - 提供语音识别超时时间设置
    - 跟踪学习时长和学习效率
- **技术实现**：
  - 扩展Word数据模型，添加以下字段：
    ```kotlin
    correctCount: Int = 0,
    familiarity: Int = 0, // 熟悉度: 0-5
    nextReviewTime: Long = 0, // 下次复习时间
    reviewCount: Int = 0 // 已复习次数
    ```
  - 实现基于熟悉度的复习间隔计算：
    ```kotlin
    // 基于熟悉度的基础间隔（单位：小时）
    val baseIntervalHours = when (familiarity) {
        0 -> 1      // 1小时后复习
        1 -> 6      // 6小时后复习
        2 -> 24     // 1天后复习
        3 -> 72     // 3天后复习
        4 -> 168    // 7天后复习
        5 -> 336    // 14天后复习
        else -> 24  // 默认1天
    }
    ```
  - 使用LiveData跟踪学习统计数据：
    ```kotlin
    private val _todayLearnedCount = MutableLiveData<Int>(0)
    private val _todayReviewCount = MutableLiveData<Int>(0)
    private val _todayStudyTime = MutableLiveData<Long>(0)
    ```

### 9. 学习数据分析与可视化
- **计划实现文件**：
  - `LearningStatsFragment.kt`：学习统计页面，展示学习数据和图表
  - `LearningStatsViewModel.kt`：管理学习统计数据
- **功能描述**：
  - **学习进度统计**：
    - 展示总单词数、已学习数和掌握数（熟悉度≥4）
    - 计算学习完成率和掌握率
  - **学习时间统计**：
    - 记录每日学习时长
    - 提供每日/每周/每月学习趋势图表
  - **错误分析**：
    - 识别用户的薄弱单词类别
    - 提供针对性学习建议
  - **学习报告**：
    - 生成学习效率和进度报告
    - 提供学习建议和改进方向
- **技术实现**：
  - 使用Room数据库存储学习统计数据
  - 使用MPAndroidChart库实现图表可视化
  - 实现学习数据分析算法，识别学习模式和薄弱点

## 待实现功能

### 1. 学习模式扩展
- 顺序学习：按添加顺序或字母顺序学习单词
- 随机学习：随机抽取单词进行学习
- 分组学习：按单词分组进行学习

### 2. 单词复习功能
#### 复习模式
- **部分实现**：
  - 已实现根据错误次数计算复习间隔时间的逻辑
  - 已实现筛选需要复习单词的方法
- **待实现**：
  - 复习界面的完整UI实现
  - 记忆曲线复习：基于艾宾浩斯记忆曲线安排复习计划
  - 错误优先复习：优先复习错误率高的单词
  - 自定义复习：用户自定义复习范围和顺序

#### 测试方式
- 选择题：提供多个选项，用户选择正确答案
- 拼写测试：用户输入单词拼写
- 听力测试：播放单词发音，用户识别单词

### 3. 学习算法优化
#### 间隔重复学习系统 (Spaced Repetition System)
- 实现基于艾宾浩斯遗忘曲线的间隔重复算法
- 根据用户对单词的掌握程度自动调整复习间隔
- 为每个单词添加"熟悉度"属性，用于计算下次复习时间
- 设计合理的遗忘曲线模型，确保学习效率最大化

### 4. 学习体验优化
#### 多样化学习模式
- 显示每个单词的错误次数和正确次数，帮助用户了解学习状态
- 拼写挑战：除了语音拼写，增加键盘输入拼写选项
- 提供多种学习反馈方式，增强学习体验

#### 个性化学习设置
- 允许用户自定义每次学习的单词数量
- 提供学习时长设置和学习提醒功能
- 允许用户调整语音识别的灵敏度和超时时间
- 支持自定义学习界面和交互方式

### 5. 数据分析与可视化
#### 学习统计
- 添加学习数据统计页面，展示学习时长、单词量、掌握率等
- 提供每日/每周/每月学习趋势图表
- 识别用户的薄弱单词类别，提供针对性建议
- 生成学习报告，帮助用户了解自己的学习效果

### 6. 设置与个性化
- 主题设置：支持浅色/深色主题切换
- 发音设置：调整发音语速、音量和语言
- 学习提醒：设置学习提醒时间和频率
- 数据备份与恢复：支持云端备份和恢复数据

### 1. 单词管理
- 添加新单词（支持英文单词和释义）
- 自动翻译功能（使用 Google Translate API）
- 支持手动编辑和删除单词
- 支持批量导入单词
- 支持单词分类管理

### 2. 学习模式
- 闪卡模式：快速浏览单词
- 测试模式：通过选择题测试记忆
- 听写模式：通过听写测试拼写
- 复习模式：根据艾宾浩斯遗忘曲线进行复习

### 3. 复习系统
- 基于艾宾浩斯遗忘曲线的间隔复习
- 错题优先复习
- 自定义复习计划
- 记忆曲线分析

### 4. 学习统计
- 学习时长统计
- 正确率统计
- 复习次数统计
- 学习进度追踪

### 5. 发音功能
- 支持单词发音
- 支持例句发音
- 支持音标显示

### 6. 翻译功能
- 自动翻译新添加的单词
- 支持手动触发翻译
- 翻译状态实时显示
- 保存翻译结果到单词数据库

## 技术架构

### 1. 数据层
- Room 数据库
- Repository 模式
- 数据实体：
  - Word（单词）
  - LearningStatistics（学习统计）
  - TranslationCache（翻译缓存）

### 2. 网络层
- Retrofit 网络请求
- Google Translate API 集成
- 网络状态管理
- 错误处理机制

### 3. 业务层
- ViewModel 架构
- 协程异步处理
- 状态管理
- 业务逻辑封装

### 4. 界面层
- Fragment 组件
- ViewBinding 视图绑定
- 自定义适配器
- 动画效果

## 开发指南

### 1. 环境配置
- Android Studio Arctic Fox 或更高版本
- Kotlin 1.5.0 或更高版本
- Gradle 7.0 或更高版本
- 最低支持 Android API 21

### 2. 依赖配置
```gradle
dependencies {
    // 核心依赖
    implementation 'androidx.core:core-ktx:1.7.0'
    implementation 'androidx.appcompat:appcompat:1.4.1'
    implementation 'com.google.android.material:material:1.5.0'
    
    // 架构组件
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.4.1'
    implementation 'androidx.navigation:navigation-fragment-ktx:2.4.1'
    implementation 'androidx.navigation:navigation-ui-ktx:2.4.1'
    
    // 数据库
    implementation 'androidx.room:room-runtime:2.4.2'
    implementation 'androidx.room:room-ktx:2.4.2'
    kapt 'androidx.room:room-compiler:2.4.2'
    
    // 网络
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.9.3'
    
    // 依赖注入
    implementation 'com.google.dagger:hilt-android:2.40.5'
    kapt 'com.google.dagger:hilt-compiler:2.40.5'
    
    // 工具库
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2'
    implementation 'com.google.code.gson:gson:2.8.9'
}
```

### 3. 项目结构
```
app/
├── src/
│   ├── main/
│   │   ├── java/com/jamshao/sonicwords/
│   │   │   ├── data/
│   │   │   │   ├── api/           # API 接口
│   │   │   │   ├── dao/           # 数据访问对象
│   │   │   │   ├── entity/        # 数据实体
│   │   │   │   └── repository/    # 数据仓库
│   │   │   ├── di/               # 依赖注入
│   │   │   ├── ui/               # 界面相关
│   │   │   │   ├── word/         # 单词管理
│   │   │   │   ├── study/        # 学习界面
│   │   │   │   └── review/       # 复习界面
│   │   │   └── utils/            # 工具类
│   │   └── res/                  # 资源文件
│   └── test/                     # 测试代码
└── build.gradle                  # 项目配置
```

### 4. 关键功能实现

#### 4.1 翻译功能
- 使用 Google Translate API 进行翻译
- 支持自动翻译和手动翻译
- 实现翻译缓存机制
- 错误处理和重试机制

```kotlin
// 翻译服务接口
interface TranslationService {
    @GET("translate")
    suspend fun translate(
        @Query("q") text: String,
        @Query("source") source: String = "en",
        @Query("target") target: String = "zh",
        @Query("key") apiKey: String
    ): TranslationResponse
}

// 翻译缓存实体
@Entity(tableName = "translation_cache")
data class TranslationCache(
    @PrimaryKey val word: String,
    val translation: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

#### 4.2 单词管理
- 支持添加、编辑、删除单词
- 自动翻译新添加的单词
- 支持批量导入
- 数据持久化

```kotlin
// 单词实体
@Entity(tableName = "words")
data class Word(
    @PrimaryKey val word: String,
    val meaning: String,
    val chineseMeaning: String?,
    val familiarity: Float = 0f,
    val correctCount: Int = 0,
    val errorCount: Int = 0,
    val reviewCount: Int = 0,
    val nextReviewTime: Long = 0L,
    val isLearned: Boolean = false
)
```

#### 4.3 学习统计
- 记录学习时长
- 统计正确率
- 追踪复习进度
- 数据可视化

```kotlin
// 学习统计实体
@Entity(tableName = "learning_statistics")
data class LearningStatistics(
    @PrimaryKey val date: String,
    val studyTime: Long = 0L,
    val reviewCount: Int = 0,
    val correctRate: Float = 0f
)
```

### 5. 开发注意事项

#### 5.1 翻译功能配置
1. 在 `TranslationApi.kt` 中配置 Google Translate API Key
2. 确保网络权限配置正确
3. 实现翻译缓存机制
4. 处理网络错误和重试逻辑

#### 5.2 数据安全
1. 个人使用无需任何加密

#### 5.3 性能优化
1. 使用协程处理异步操作
2. 实现数据缓存机制
3. 优化数据库查询
4. 减少不必要的网络请求

### 6. 测试
- 单元测试
- UI 测试
- 集成测试
- 性能测试

### 7. 发布
- 版本管理
- 应用签名
- 混淆配置
- 发布检查清单

## 贡献指南
1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证
MIT License

## 最近更新记录

### 2023-04-15：语音识别升级

#### 主要更改：
1. **语音识别引擎升级**：
   - 从Google在线语音识别服务迁移至Vosk离线语音识别引擎
   - 实现无网络环境下的语音识别功能
   - 消除了对Google Play Services的依赖

2. **字母级别的拼写功能**：
   - 增加了逐字母拼写单词的功能
   - 实现对每个字母的单独识别和评估
   - 添加了实时反馈机制，包括视觉和语音提示

3. **用户体验优化**：
   - 改进按钮交互方式，使用按下开始、松开结束的直观操作方式
   - 添加了颜色反馈，使学习过程更加直观
   - 优化了提示信息，提供更清晰的引导

4. **性能提升**：
   - 减少了网络依赖，提高了应用的响应速度
   - 降低了电量消耗，适合长时间学习场景
   - 优化了内存使用，提高了整体性能