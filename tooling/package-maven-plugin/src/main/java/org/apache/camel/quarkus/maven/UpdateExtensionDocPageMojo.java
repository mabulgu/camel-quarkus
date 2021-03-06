/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.maven;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import freemarker.template.Configuration;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.apache.camel.catalog.Kind;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "update-extension-doc-page", threadSafe = true)
public class UpdateExtensionDocPageMojo extends AbstractDocGeneratorMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Charset charset = Charset.forName(encoding);
        final Path basePath = baseDir.toPath();

        if (!"runtime".equals(basePath.getFileName().toString())) {
            getLog().info("Skipping a module that is not a Quarkus extension runtime module");
            return;
        }

        final CqCatalog catalog = CqCatalog.getThreadLocalCamelCatalog();

        final Path multiModuleProjectDirectoryPath = multiModuleProjectDirectory.toPath();
        final CamelQuarkusExtension ext = CamelQuarkusExtension.read(basePath.resolve("pom.xml"));

        final Configuration cfg = CqUtils.getTemplateConfig(basePath, AbstractDocGeneratorMojo.DEFAULT_TEMPLATES_URI_BASE,
                templatesUriBase, encoding);

        final List<ArtifactModel<?>> models = catalog.filterModels(ext.getRuntimeArtifactIdBase())
                .sorted(BaseModel.compareTitle())
                .collect(Collectors.toList());

        final Map<String, Object> model = new HashMap<>();
        model.put("artifactIdBase", ext.getRuntimeArtifactIdBase());
        model.put("firstVersion", ext.getFirstVersion().get());
        model.put("nativeSupported", ext.isNativeSupported());
        model.put("name", ext.getName().get());
        model.put("intro", loadSection(basePath, "intro.adoc", charset,
                CqUtils.getDescription(models, ext.getDescription().orElse(null), getLog())));
        model.put("models", models);
        model.put("usage", loadSection(basePath, "usage.adoc", charset, null));
        model.put("configuration", loadSection(basePath, "configuration.adoc", charset, null));
        model.put("limitations", loadSection(basePath, "limitations.adoc", charset, null));
        model.put("humanReadableKind", new TemplateMethodModelEx() {
            @Override
            public Object exec(List arguments) throws TemplateModelException {
                if (arguments.size() != 1) {
                    throw new TemplateModelException("Wrong argument count in toCamelCase()");
                }
                return CqUtils.humanReadableKind(Kind.valueOf(String.valueOf(arguments.get(0))));
            }
        });

        final Path docPagePath = multiModuleProjectDirectoryPath
                .resolve("docs/modules/ROOT/pages/extensions/" + ext.getRuntimeArtifactIdBase() + ".adoc");
        try {
            Files.createDirectories(docPagePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Could not create directories " + docPagePath.getParent(), e);
        }
        String pageText = "// Do not edit directly!\n// This file was generated by camel-quarkus-package-maven-plugin:update-extension-doc-page\n\n"
                + evalTemplate(cfg, "extension-doc-page.adoc", model, new StringWriter()).toString();
        try {
            Files.write(docPagePath, pageText.getBytes(charset));
        } catch (IOException e) {
            throw new RuntimeException("Could not write to " + docPagePath, e);
        }
    }

    private static String loadSection(Path basePath, String fileName, Charset charset, String default_) {
        Path p = basePath.resolve("src/main/doc/" + fileName);
        if (Files.exists(p)) {
            try {
                final String result = new String(Files.readAllBytes(p), charset);
                if (!result.endsWith("\n")) {
                    return result + "\n";
                }
                return result;
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + p, e);
            }
        } else {
            return default_;
        }
    }

}
