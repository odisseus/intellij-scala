package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j.BspConnectionDetails
import com.intellij.ide.util.projectWizard.{ModuleWizardStep, WizardContext}
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBList
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager, Spacer}
import com.intellij.util.ui.UI
import org.jetbrains.annotations.Nls
import org.jetbrains.bsp.project.importing.BspSetupConfigStep.BspConfigSetupTask
import org.jetbrains.bsp.project.importing.bspConfigSteps._
import org.jetbrains.bsp.project.importing.setup.{BspConfigSetup, FastpassConfigSetup, NoConfigSetup, SbtConfigSetup}
import org.jetbrains.bsp.protocol.BspConnectionConfig
import org.jetbrains.bsp.settings.BspProjectSettings._
import org.jetbrains.bsp.{BspBundle, BspUtil}
import org.jetbrains.plugins.scala.build.IndicatorReporter
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.SbtProjectImportProvider

import java.awt.BorderLayout
import java.io.File
import java.nio.file.Path
import javax.swing.{DefaultListModel, JComponent, JPanel, ListSelectionModel}

object bspConfigSteps {

  sealed private[importing] abstract class ConfigSetup
  case object NoSetup extends ConfigSetup
  case object SbtSetup extends ConfigSetup
  case object BloopSetup extends ConfigSetup
  case object BloopSbtSetup extends ConfigSetup
  case object MillSetup extends ConfigSetup
  case object FastpassSetup extends ConfigSetup

  private[importing] def configChoiceName(configs: ConfigSetup) = configs match {
    case NoSetup => BspBundle.message("bsp.config.steps.choice.no.setup")
    case SbtSetup => BspBundle.message("bsp.config.steps.choice.sbt")
    case BloopSetup => BspBundle.message("bsp.config.steps.choice.bloop")
    case BloopSbtSetup => BspBundle.message("bsp.config.steps.choice.sbt.with.bloop")
    case MillSetup => BspBundle.message("bsp.config.steps.choice.mill")
    case FastpassSetup => BspBundle.message("bsp.config.steps.choice.fastpass")
  }

  private[importing] def configName(config: BspConnectionDetails) =
    s"${config.getName} ${config.getVersion}"

  private[importing] def withTooltip(component: JComponent, @Nls tooltip: String) =
    UI.PanelFactory.panel(component).withTooltip(tooltip).createPanel()

  private[importing] def addTitledList(parent: JComponent, title: JComponent, list: JBList[String]): Unit = {
    val manager = new GridLayoutManager(3,1)
    manager.setSameSizeVertically(false)
    parent.setLayout(manager)

    val titleConstraints = new GridConstraints()
    titleConstraints.setRow(0)
    titleConstraints.setFill(GridConstraints.FILL_HORIZONTAL)
    parent.add(title, titleConstraints)

    val listConstraints = new GridConstraints()
    listConstraints.setRow(1)
    listConstraints.setFill(GridConstraints.FILL_BOTH)
    listConstraints.setIndent(1)
    parent.add(list, listConstraints)

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

    val spacer = new Spacer()
    val spacerConstraints = new GridConstraints()
    spacerConstraints.setRow(2)
    spacerConstraints.setVSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW)
    parent.add(spacer, spacerConstraints)
  }

  def configSetupChoices(workspace: File): List[ConfigSetup] = {
    val workspaceConfigs = workspaceSetupChoices(workspace)
    if (workspaceConfigs.nonEmpty) workspaceConfigs
    else List(NoSetup)
  }

  def configureBuilder(
    builder: BspProjectImportBuilder,
    workspace: File,
    configSetup: ConfigSetup
  ): BspConfigSetup = {
    val BuilderConfigurationParameters(
      setup: BspConfigSetup,
      preImportConfig: Option[PreImportConfig],
      serverConfig: Option[BspServerConfig],
      externalBspWorkspace: Option[Path]
    ) = getBuilderConfigurationParameters(workspace, configSetup)

    preImportConfig.foreach(builder.setPreImportConfig)
    serverConfig.foreach(builder.setServerConfig)
    externalBspWorkspace.foreach(builder.setExternalBspWorkspace)

    setup
  }

  case class BuilderConfigurationParameters(
    bspConfigSetup: BspConfigSetup,
    preImportConfig: Option[PreImportConfig],
    serverConfig: Option[BspServerConfig],
    externalBspWorkspace: Option[Path]
  )

  def getBuilderConfigurationParameters(
    workspace: File,
    configSetup: ConfigSetup
  ): BuilderConfigurationParameters = {
    val workspaceBspConfigs = BspConnectionConfig.workspaceBspConfigs(workspace)

    val tuple = if (workspaceBspConfigs.size == 1)
      (NoConfigSetup, Some(NoPreImport), Some(BspConfigFile(workspaceBspConfigs.head._1.toPath)), None)
    else configSetup match {
      case bspConfigSteps.NoSetup =>
        (NoConfigSetup, Some(AutoPreImport), Some(AutoConfig), None)
      case bspConfigSteps.BloopSetup =>
        (NoConfigSetup, Some(NoPreImport), Some(BloopConfig), None)
      case bspConfigSteps.BloopSbtSetup =>
        (NoConfigSetup, Some(BloopSbtPreImport), Some(BloopConfig), None)
      case bspConfigSteps.SbtSetup =>
        (SbtConfigSetup(workspace), Some(NoPreImport), None, None) // server config to be set in next step
      case bspConfigSteps.MillSetup =>
        (NoConfigSetup, Some(MillBspPreImport), Some(AutoConfig), None)
      case bspConfigSteps.FastpassSetup =>
        val bspWorkspace = FastpassConfigSetup.computeBspWorkspace(workspace)
        val configSetup: BspConfigSetup = FastpassConfigSetup.create(workspace).fold(throw _, identity)
        (configSetup, Some(NoPreImport), None, Some(bspWorkspace))
    }
    BuilderConfigurationParameters.tupled.apply(tuple)
  }

  def workspaceSetupChoices(workspace: File): List[ConfigSetup] = {

    val vfile = LocalFileSystem.getInstance().findFileByIoFile(workspace)

    val sbtChoice = if (SbtProjectImportProvider.canImport(vfile)) {
      val sbtVersion = Version(detectSbtVersion(workspace, getDefaultLauncher))
      if (sbtVersion.major(2) >= Version("1.4")) {
        // sbt >= 1.4 : user choose: bloop or sbt
        List(SbtSetup, BloopSbtSetup)
      } else {
        List(BloopSbtSetup)
      }
    } else Nil

    val millChoice =
      if (MillProjectImportProvider.canImport(vfile.toNioPath.toFile)) List(MillSetup)
      else Nil

    val bloopChoice =
      if (BspUtil.bloopConfigDir(workspace).isDefined) List(BloopSetup)
      else Nil

    val fastpassChoice = if(FastpassProjectImportProvider.canImport(vfile))
      List(FastpassSetup)
    else
      Nil


    (sbtChoice ++ millChoice ++ bloopChoice ++ fastpassChoice).distinct
  }
}

class BspSetupConfigStep(wizardContext: WizardContext, builder: BspProjectImportBuilder, setupTaskWorkspace: File)
  extends ModuleWizardStep {

  private var runSetupTask: BspConfigSetup = NoConfigSetup

  private val workspaceBspConfigs = BspConnectionConfig.workspaceBspConfigs(setupTaskWorkspace)
  private lazy val workspaceSetupConfigs: List[ConfigSetup] = workspaceSetupChoices(setupTaskWorkspace)

  private val configSetupChoices: List[ConfigSetup] = {
    if (workspaceBspConfigs.size == 1) List(NoSetup)
    else if (workspaceSetupConfigs.nonEmpty) workspaceSetupConfigs
    else List(NoSetup)
  }

  private val bspSetupConfigStepUi = new BspSetupConfigStepUi(BspBundle.message("bsp.config.steps.setup.config.choose.tool"), configSetupChoices)


  override def getComponent: JComponent = bspSetupConfigStepUi.mainComponent

  override def getPreferredFocusedComponent: JComponent = bspSetupConfigStepUi.chooseBspSetupList

  override def validate(): Boolean = {
    workspaceBspConfigs.nonEmpty ||
      configSetupChoices.size == 1 ||
      bspSetupConfigStepUi.chooseBspSetupList.getSelectedIndex >= 0
  }

  override def updateStep(): Unit = {
    bspSetupConfigStepUi.updateChooseBspSetupComponent(configSetupChoices)
  }

  override def updateDataModel(): Unit = {

    val configIndex =
      if (configSetupChoices.size == 1) 0
      else bspSetupConfigStepUi.chooseBspSetupList.getSelectedIndex

    runSetupTask =
      if (configSetupChoices.size > configIndex && configIndex >= 0)
        configureBuilder(builder, setupTaskWorkspace, configSetupChoices(configIndex))
      else NoConfigSetup
  }

  override def isStepVisible: Boolean = {
    builder.preImportConfig == AutoPreImport &&
      configSetupChoices.size > 1 &&
      workspaceBspConfigs.isEmpty
  }

  override def onWizardFinished(): Unit = {
    // TODO this spawns an indicator window which is not nice.
    // show a live log in the window or something?
    if (wizardContext.getProjectBuilder.isInstanceOf[BspProjectImportBuilder]) {
      updateDataModel() // without it runSetupTask is null
      builder.prepare(wizardContext)
      //this will use DefaultProject, which will lead to exception IDEA-289729
      //builder.ensureProjectIsDefined(wizardContext)
      val task = new BspConfigSetupTask(runSetupTask)
      task.queue()
    }
  }

}
object BspSetupConfigStep {

  private[importing] class BspConfigSetupTask(setup: BspConfigSetup)
    extends Task.Modal(null, BspBundle.message("bsp.config.steps.setup.config.task.title"), true) {

    override def run(indicator: ProgressIndicator): Unit = {
      val reporter = new IndicatorReporter(indicator)
      setup.run(reporter)
    }

    override def onCancel(): Unit =
      setup.cancel()
  }
}

final class BspSetupConfigStepUi(
  @NlsContexts.Separator title: String,
  configSetups: Seq[ConfigSetup]
) {

  val mainComponent = new JPanel()
  private val chooseBspSetupModel = new DefaultListModel[String]
  val chooseBspSetupList = new JBList[String](chooseBspSetupModel)

  locally {
    val chooseSetupTitle = new TitledSeparator(title)
    val titleWithTip = withTooltip(chooseSetupTitle, BspBundle.message("bsp.config.steps.setup.config.choose.tool.tooltip"))
    addTitledList(mainComponent, titleWithTip, chooseBspSetupList)
  }

  def selectedConfigSetup: ConfigSetup =
    configSetups(chooseBspSetupList.getSelectedIndex)

  def updateChooseBspSetupComponent(configSetupChoices: Seq[ConfigSetup]): Unit = {
    val setupChoicesStrings = getConfigSetupChoicesStrings(configSetupChoices)
    chooseBspSetupModel.clear()
    setupChoicesStrings.foreach(chooseBspSetupModel.addElement)
    chooseBspSetupList.setSelectedIndex(0)
  }

  private def getConfigSetupChoicesStrings(configSetupChoices: Seq[ConfigSetup]): Seq[String] = {
    val recommendedSuffix = BspBundle.message("bsp.config.steps.choose.config.recommended.suffix")
    val configChoiceName = configSetupChoices.map(bspConfigSteps.configChoiceName)
    configChoiceName match {
      case Seq(head, tail@_*) =>
        s"$head ($recommendedSuffix)" +: tail
      case Nil =>
        Nil
    }
  }
}

class BspChooseConfigStep(context: WizardContext, builder: BspProjectImportBuilder)
  extends ModuleWizardStep {

  private val myComponent = new JPanel(new BorderLayout)
  private val chooseBspConfig = new JBList[String]()
  private val chooseBspSetupModel = new DefaultListModel[String]
  chooseBspConfig.setModel(chooseBspSetupModel)

  private def bspConfigs = BspConnectionConfig.allBspConfigs(context.getProjectDirectory.toFile)

  {
    val chooseSetupTitle = new TitledSeparator(BspBundle.message("bsp.config.steps.choose.config.title"))
    val titleWithTip = withTooltip(chooseSetupTitle, BspBundle.message("bsp.config.steps.choose.config.title.tooltip"))
    addTitledList(myComponent, titleWithTip, chooseBspConfig)
  }

  override def getComponent: JComponent = myComponent

  override def validate(): Boolean = {
    // config already chosen in previous step
    val alreadySet = builder.serverConfig != AutoConfig

    // there should be at least one config at this point
    val configsExist = !chooseBspConfig.isEmpty
    val configSelected = (chooseBspConfig.getItemsCount == 1 || chooseBspConfig.getSelectedIndex >= 0)

    alreadySet || (configsExist && configSelected)
  }

  override def updateStep(): Unit = {
    chooseBspSetupModel.clear()
    bspConfigs
      .map { case (_,details) => configName(details) }
      .foreach(chooseBspSetupModel.addElement)
  }

  override def updateDataModel(): Unit = {
    val configIndex =
      if (chooseBspConfig.getItemsCount == 1) 0
      else chooseBspConfig.getSelectedIndex

    if (configIndex >= 0) {
      val (file,_) = bspConfigs(configIndex)
      val config = BspConfigFile(file.toPath)
      builder.setServerConfig(config)
    }
  }

  override def onWizardFinished(): Unit = {
    updateStep()
    if (builder.serverConfig == AutoConfig && chooseBspConfig.getItemsCount == 1) {
      val (file,_) = bspConfigs.head
      val config = BspConfigFile(file.toPath)
      builder.setServerConfig(config)
    }
  }

  override def isStepVisible: Boolean = {
    updateStep()
    builder.serverConfig == AutoConfig && chooseBspConfig.getItemsCount > 1
  }
}
