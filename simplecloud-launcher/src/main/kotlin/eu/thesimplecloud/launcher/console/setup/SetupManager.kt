package eu.thesimplecloud.launcher.console.setup

import com.google.common.collect.Queues
import eu.thesimplecloud.launcher.console.setup.annotations.SetupCancelled
import eu.thesimplecloud.launcher.console.setup.annotations.SetupFinished
import eu.thesimplecloud.launcher.console.setup.annotations.SetupQuestion
import eu.thesimplecloud.launcher.startup.Launcher
import eu.thesimplecloud.lib.stringparser.StringParser
import org.jetbrains.annotations.NotNull
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Consumer
import kotlin.collections.ArrayList

class SetupManager(val launcher: Launcher) {

    val logger = launcher.logger
    val setupQueue = ArrayList<SetupData>()
    var currentSetup: SetupData? = null
    var currentQuestion: SetupQuestionData? = null
        private set
    private var currentQuestionIndex = 0

    private val allSetupsCompletedCallbacks = ArrayList<Consumer<Unit>>()

    fun queueSetup(setup: ISetup, first: Boolean = false) {
        val questions = ArrayList<SetupQuestionData>()
        val methods = setup::class.java.methods
        methods.filter { it.isAnnotationPresent(SetupQuestion::class.java) }.forEach { method ->
            check(method.parameters.size == 1) { "Function marked with SetupQuestion must have one parameter." }
            questions.add(SetupQuestionData(method.getAnnotation(SetupQuestion::class.java), method, method.parameters[0]))
        }

        val setupFinishedMethods = methods.filter { it.isAnnotationPresent(SetupFinished::class.java) }
        val setupCancelledMethods = methods.filter { it.isAnnotationPresent(SetupCancelled::class.java) }
        check(setupFinishedMethods.size <= 1) { "Only one function in a setup can be marked with SetupFinished." }
        check(setupCancelledMethods.size <= 1) { "Only one function in a setup can be marked with SetupCancelled." }
        val finishedMethod = setupFinishedMethods.firstOrNull()
        val cancelledMethod = setupCancelledMethods.firstOrNull()
        finishedMethod?.let { check(it.parameters.isEmpty()) { "The function marked with SetupFinished must have 0 parameters." } }
        cancelledMethod?.let { check(it.parameters.isEmpty()) { "The function marked with SetupFinished must have 0 parameters." } }

        val setupData = SetupData(setup, cancelledMethod, finishedMethod, questions)
        if (this.currentSetup == null) {
            startSetup(setupData)
            return
        }
        if (first)
            this.setupQueue.add(0, setupData)
        else
            this.setupQueue.add(setupData)
    }

    private fun startSetup(setupData: SetupData) {
        this.currentSetup = setupData
        this.currentQuestion = setupData.questions[currentQuestionIndex]
        this.launcher.consoleSender.sendMessage("launcher.setup-started", "Setup started. To abort the setup write \"exit\".")
    }

    fun onResponse(response: String) {
        val currentQuestion = this.currentQuestion ?: return
        val parsedValue = StringParser().parserString(response, currentQuestion.prameter.type)
        val invokeResponse = try {
            currentQuestion.method.invoke(this.currentSetup!!.source, parsedValue)
        } catch (e: InvocationTargetException) {
            this.launcher.consoleSender.sendMessage("launcher.setup.invalid-response", "Invalid response.")
            return
        }
        if (invokeResponse is Boolean && invokeResponse == false) {
            return
        }
        nextQuestion()
    }

    fun onAllSetupsCompleted(consumer: Consumer<Unit>) {
        if (this.currentSetup == null) {
            consumer.accept(Unit)
            return
        }
        allSetupsCompletedCallbacks.add(consumer)
    }

    private fun nextQuestion() {
        val activeSetup = this.currentSetup ?: return
        if (!hasNextQuestion(activeSetup)) {
            finishCurrentSetup()
            return
        }
        this.currentQuestionIndex++
        this.currentQuestion = activeSetup.questions[currentQuestionIndex]
        this.logger.updatePrompt(false)
    }

    private fun finishCurrentSetup() {
        val currentSetupReference = this.currentSetup
        resetSetup()
        currentSetupReference?.callFinishedMethod()
        this.logger.success("Setup completed.")
        checkForNextSetup()
    }

    fun cancelCurrentSetup() {
        val currentSetupReference = this.currentSetup
        resetSetup()
        currentSetupReference?.callCancelledMethod()
        this.logger.warning("Setup cancelled.")
        checkForNextSetup()
    }

    private fun checkForNextSetup() {
        if (this.setupQueue.isEmpty()) {
            this.allSetupsCompletedCallbacks.forEach { it.accept(Unit) }
        }
        if (this.setupQueue.isNotEmpty()) {
            startSetup(this.setupQueue.removeAt(0))
        }
    }

    private fun resetSetup() {
        this.currentSetup = null
        this.currentQuestion = null
        this.currentQuestionIndex = 0
    }

    private fun hasNextQuestion(setupData: SetupData) = this.currentQuestionIndex + 1 in setupData.questions.indices


    class SetupData(val source: ISetup, val cancelledMethod: Method?, val finishedMethod: Method?, val questions: List<SetupQuestionData>) {

        fun callFinishedMethod() {
            finishedMethod?.invoke(source)
        }

        fun callCancelledMethod() {
            cancelledMethod?.invoke(source)
        }

    }

    class SetupQuestionData(val setupQuestion: SetupQuestion, val method: Method, val prameter: Parameter)

}