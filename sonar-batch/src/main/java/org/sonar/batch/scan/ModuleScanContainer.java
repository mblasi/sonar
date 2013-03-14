/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2012 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.batch.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchExtension;
import org.sonar.api.batch.BatchExtensionDictionnary;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.platform.ComponentContainer;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Languages;
import org.sonar.api.resources.Project;
import org.sonar.api.scan.filesystem.FileExclusions;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.batch.DefaultProfileLoader;
import org.sonar.batch.DefaultProjectClasspath;
import org.sonar.batch.DefaultSensorContext;
import org.sonar.batch.DefaultTimeMachine;
import org.sonar.batch.ProfileProvider;
import org.sonar.batch.ProjectTree;
import org.sonar.batch.ResourceFilters;
import org.sonar.batch.ViolationFilters;
import org.sonar.batch.bootstrap.ExtensionInstaller;
import org.sonar.batch.bootstrap.ExtensionUtils;
import org.sonar.batch.components.TimeMachineConfiguration;
import org.sonar.batch.events.EventBus;
import org.sonar.batch.index.DefaultIndex;
import org.sonar.batch.index.ResourcePersister;
import org.sonar.batch.local.DryRunExporter;
import org.sonar.batch.phases.Phases;
import org.sonar.batch.phases.PhasesTimeProfiler;
import org.sonar.batch.scan.filesystem.DeprecatedFileSystemAdapter;
import org.sonar.batch.scan.filesystem.ExclusionFilters;
import org.sonar.batch.scan.filesystem.FileSystemLogger;
import org.sonar.batch.scan.filesystem.LanguageFilters;
import org.sonar.batch.scan.filesystem.ModuleFileSystemProvider;

public class ModuleScanContainer extends ComponentContainer {
  private static final Logger LOG = LoggerFactory.getLogger(ModuleScanContainer.class);
  private final Project module;

  public ModuleScanContainer(ProjectScanContainer parent, Project module) {
    super(parent);
    this.module = module;
  }

  @Override
  protected void doBeforeStart() {
    LOG.info("-------------  Scan {}", module.getName());
    addCoreComponents();
    addExtensions();
  }

  private void addCoreComponents() {
    ProjectDefinition moduleDefinition = get(ProjectTree.class).getProjectDefinition(module);
    add(
        moduleDefinition,
        module.getConfiguration(),
        module,
        ModuleSettings.class);

    // hack to initialize commons-configuration before ExtensionProviders
    get(ModuleSettings.class);

    add(
        EventBus.class,
        Phases.class,
        PhasesTimeProfiler.class,
        UnsupportedProperties.class,
        Phases.getPhaseClasses(),
        moduleDefinition.getContainerExtensions(),

        // TODO move outside project, but not possible yet because of dependency of project settings (cf plsql)
        Languages.class,

        // file system
        PathResolver.class,
        FileExclusions.class,
        LanguageFilters.class,
        ExclusionFilters.class,
        DefaultProjectClasspath.class,
        new ModuleFileSystemProvider(),
        DeprecatedFileSystemAdapter.class,
        FileSystemLogger.class,

        // the Snapshot component will be removed when asynchronous measures are improved (required for AsynchronousMeasureSensor)
        get(ResourcePersister.class).getSnapshot(module),

        TimeMachineConfiguration.class,
        org.sonar.api.database.daos.MeasuresDao.class,
        DefaultSensorContext.class,
        BatchExtensionDictionnary.class,
        DefaultTimeMachine.class,
        ViolationFilters.class,
        ResourceFilters.class,
        DefaultProfileLoader.class,
        DryRunExporter.class,
        new ProfileProvider());
  }

  private void addExtensions() {
    ExtensionInstaller installer = get(ExtensionInstaller.class);
    installer.install(this, new ExtensionInstaller.ComponentFilter() {
      public boolean accept(Object extension) {
        if (ExtensionUtils.isType(extension, BatchExtension.class) && ExtensionUtils.isInstantiationStrategy(extension, InstantiationStrategy.PER_PROJECT)) {
          // Special use-case: the extension point ProjectBuilder is used in a Maven environment to define some
          // new sub-projects without pom.
          // Example : C# plugin adds sub-projects at runtime, even if they are not defined in root pom.
          return !ExtensionUtils.isMavenExtensionOnly(extension) || module.getPom() != null;
        }
        return false;
      }
    });
  }

  @Override
  protected void doAfterStart() {
    DefaultIndex index = get(DefaultIndex.class);
    index.setCurrentProject(module,
        get(ResourceFilters.class),
        get(ViolationFilters.class),
        get(RulesProfile.class));

    get(Phases.class).execute(module);
  }


}