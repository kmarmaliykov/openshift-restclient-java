/*******************************************************************************
 * Copyright (c) 2015 Red Hat, Inc. Distributed under license by Red Hat, Inc.
 * All rights reserved. This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Red Hat, Inc.
 ******************************************************************************/
package com.openshift.internal.restclient.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.jboss.dmr.ModelNode;

import com.openshift.internal.restclient.OpenShiftAPIVersion;
import com.openshift.internal.restclient.model.build.CustomBuildStrategy;
import com.openshift.internal.restclient.model.build.DockerBuildStrategy;
import com.openshift.internal.restclient.model.build.GitBuildSource;
import com.openshift.internal.restclient.model.build.ImageChangeTrigger;
import com.openshift.internal.restclient.model.build.STIBuildStrategy;
import com.openshift.internal.restclient.model.build.SourceBuildStrategy;
import com.openshift.internal.restclient.model.build.WebhookTrigger;
import com.openshift.restclient.IClient;
import com.openshift.restclient.images.DockerImageURI;
import com.openshift.restclient.model.IBuildConfig;
import com.openshift.restclient.model.build.BuildSourceType;
import com.openshift.restclient.model.build.BuildStrategyType;
import com.openshift.restclient.model.build.BuildTriggerType;
import com.openshift.restclient.model.build.IBuildSource;
import com.openshift.restclient.model.build.IBuildStrategy;
import com.openshift.restclient.model.build.IBuildTrigger;
import com.openshift.restclient.model.build.ICustomBuildStrategy;
import com.openshift.restclient.model.build.IDockerBuildStrategy;
import com.openshift.restclient.model.build.IGitBuildSource;
import com.openshift.restclient.model.build.IImageChangeTrigger;
import com.openshift.restclient.model.build.ISTIBuildStrategy;
import com.openshift.restclient.model.build.IWebhookTrigger;

/**
 * @author Jeff Cantrill
 */
public class BuildConfig extends KubernetesResource implements IBuildConfig {

	public BuildConfig(ModelNode node, IClient client, Map<String, String []> propertyKeys) {
		super(node, client, propertyKeys);
		//TODO add check to kind here
	}
	
	@Override
	public List<IBuildTrigger> getBuildTriggers() {
		List<IBuildTrigger> triggers = new ArrayList<IBuildTrigger>();
		List<ModelNode> list = get(BUILDCONFIG_TRIGGERS).asList();
		final String name = getName();
		final String url = getClient() != null ? getClient().getBaseURL().toString() : "";
		final String version = getClient() != null ? getClient().getOpenShiftAPIVersion() : "";
		for (ModelNode node : list) {
			BuildTriggerType type = BuildTriggerType.valueOf(node.get("type").asString());
			switch(type){
				case generic:
				case Generic:
					triggers.add(new WebhookTrigger(type, asString(node, BUILD_CONFIG_WEBHOOK_GENERIC_SECRET), name, url, version,getNamespace()));
					break;
				case github:
				case GitHub:
					triggers.add(new WebhookTrigger(type, asString(node, BUILD_CONFIG_WEBHOOK_GITHUB_SECRET), name, url, version, getNamespace()));
					break;
				case imageChange:
				case ImageChange:
					triggers.add(new ImageChangeTrigger(type,
							asString(node, BUILD_CONFIG_IMAGECHANGE_IMAGE),
							asString(node, BUILD_CONFIG_IMAGECHANGE_NAME),
							asString(node, BUILD_CONFIG_IMAGECHANGE_TAG))
					);
					break;
				default:
			}
		}
		return triggers;
	}

	@Override
	public void addBuildTrigger(IBuildTrigger trigger) {
		ModelNode triggers = get(BUILDCONFIG_TRIGGERS);
		ModelNode triggerNode = triggers.add();
		switch(trigger.getType()) {
		case generic:
		case Generic:
			if(!(trigger instanceof IWebhookTrigger)) {
				throw new IllegalArgumentException("IBuildTrigger of type generic does not implement IWebhookTrigger");
			}
			IWebhookTrigger generic = (IWebhookTrigger)trigger;
			triggerNode.get(getPath(BUILD_CONFIG_WEBHOOK_GENERIC_SECRET)).set(generic.getSecret());
			break;
		case github:
		case GitHub:
			if(!(trigger instanceof IWebhookTrigger)) {
				throw new IllegalArgumentException("IBuildTrigger of type github does not implement IWebhookTrigger");
			}
			IWebhookTrigger github = (IWebhookTrigger)trigger;
			triggerNode.get(getPath(BUILD_CONFIG_WEBHOOK_GITHUB_SECRET)).set(github.getSecret());
			break;
		case imageChange:
		case ImageChange:
			if(!(trigger instanceof IImageChangeTrigger)) {
				throw new IllegalArgumentException("IBuildTrigger of type imageChange does not implement IImageChangeTrigger");
			}
			IImageChangeTrigger image = (IImageChangeTrigger)trigger;
			triggerNode.get(getPath(BUILD_CONFIG_IMAGECHANGE_IMAGE)).set(defaultIfNull(image.getImage()));
			triggerNode.get(getPath(BUILD_CONFIG_IMAGECHANGE_NAME)).set(defaultIfNull(image.getFrom()));
			triggerNode.get(getPath(BUILD_CONFIG_IMAGECHANGE_TAG)).set(StringUtils.defaultIfBlank(image.getTag(), ""));
			break;
		}
		triggerNode.get("type").set(trigger.getType().name());
	}
	
	private String defaultIfNull(DockerImageURI uri) {
		if(uri == null) return "";
		return uri.toString();
	}

	@Override
	public String getOutputRepositoryName() {
		return asString(BUILDCONFIG_OUTPUT_REPO);
	}

	public String getSourceURI() {
		return asString(BUILDCONFIG_SOURCE_URI);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T extends IBuildSource> T getBuildSource() {
		switch(BuildSourceType.valueOf(asString(BUILDCONFIG_SOURCE_TYPE))){
		case Git:
			return (T) new GitBuildSource(asString(BUILDCONFIG_SOURCE_URI), asString(BUILDCONFIG_SOURCE_REF));
		default:
		}
		return null;
	}

	@Override
	public void setBuildSource(IBuildSource source){
		switch(source.getType()) {
		case Git:
			if(!(source instanceof IGitBuildSource)) {
				throw new IllegalArgumentException("IBuildSource of type Git does not implement IGitBuildSource");
			}
			IGitBuildSource git = (IGitBuildSource) source;
			set(BUILDCONFIG_SOURCE_REF, git.getRef());
			break;
		}
		set(BUILDCONFIG_SOURCE_URI, source.getURI());
		set(BUILDCONFIG_SOURCE_TYPE, source.getType().name());
	}
	
	@Override
	public void setBuildStrategy(IBuildStrategy strategy) {
		// Remove other strategies if already set?
		switch(strategy.getType()) {
		case Custom:
			if ( !(strategy instanceof ICustomBuildStrategy)) {
				throw new IllegalArgumentException("IBuildStrategy of type Custom does not implement ICustomBuildStrategy");
			}
			ICustomBuildStrategy custom = (ICustomBuildStrategy)strategy;
			if(custom.getImage() != null) {
				set(BUILDCONFIG_CUSTOM_IMAGE, custom.getImage().toString());
			}
			set(BUILDCONFIG_CUSTOM_EXPOSEDOCKERSOCKET, custom.exposeDockerSocket());
			if(custom.getEnvironmentVariables() != null) {
				setEnvMap(BUILDCONFIG_CUSTOM_ENV, custom.getEnvironmentVariables());
			}
			break;
		case STI:
		case Source:
			if ( !(strategy instanceof ISTIBuildStrategy)) {
				throw new IllegalArgumentException("IBuildStrategy of type Custom does not implement ISTIBuildStrategy");
			}
			ISTIBuildStrategy sti = (ISTIBuildStrategy)strategy;
			if(sti.getImage() != null) {
				set(BUILDCONFIG_STI_IMAGE, sti.getImage().toString());
			}
			if(sti.getScriptsLocation() != null) {
				set(BUILDCONFIG_STI_SCRIPTS, sti.getScriptsLocation());
			}
			if(OpenShiftAPIVersion.v1beta1.name().equals(getApiVersion())) {
				set(BUILDCONFIG_STI_CLEAN, sti.forceClean());
			} else{
				set(BUILDCONFIG_STI_INCREMENTAL, sti.incremental());
			}
			if(sti.getEnvironmentVariables() != null) {
				setEnvMap(BUILDCONFIG_STI_ENV, sti.getEnvironmentVariables());
			}
			break;
		case Docker:
			if ( !(strategy instanceof IDockerBuildStrategy)) {
				throw new IllegalArgumentException("IBuildStrategy of type Custom does not implement IDockerBuildStrategy");
			}
			IDockerBuildStrategy docker = (IDockerBuildStrategy)strategy;
			if(docker.getBaseImage() != null) {
				set(BUILDCONFIG_DOCKER_BASEIMAGE, docker.getBaseImage().toString());
			}
			if(docker.getContextDir() != null) {
				set(BUILDCONFIG_DOCKER_CONTEXTDIR, docker.getContextDir());
			}
			set(BUILDCONFIG_DOCKER_NOCACHE, docker.isNoCache());
			break;
		}

		set(BUILDCONFIG_TYPE, strategy.getType().name());
	}
	
	public void setOutput(DockerImageURI imageUri){
		//FIXME
//		ModelNode output = getNode().get(new String []{"parameters","output"});
//		output.get("imageTag").set(imageUri.getUriWithoutHost());
//		output.get("registry").set(imageUri.getRepositoryHost());
	}

	@SuppressWarnings("unchecked")
	@Override
	public  <T extends IBuildStrategy> T getBuildStrategy() {
		switch(BuildStrategyType.valueOf(asString(BUILDCONFIG_TYPE))){
		case Custom:
			return (T) new CustomBuildStrategy(
						asString(BUILDCONFIG_CUSTOM_IMAGE),
						asBoolean(BUILDCONFIG_CUSTOM_EXPOSEDOCKERSOCKET),
						getEnvMap(BUILDCONFIG_CUSTOM_ENV)
					);
		case STI:
		case Source:
			if(OpenShiftAPIVersion.v1beta1.name().equals(getApiVersion())) {
				return (T) new STIBuildStrategy(asString(BUILDCONFIG_STI_IMAGE),
						asString(BUILDCONFIG_STI_SCRIPTS),
						!asBoolean(BUILDCONFIG_STI_CLEAN),
						getEnvMap(BUILDCONFIG_STI_ENV)
						);
			}
			return (T) new SourceBuildStrategy(asString(BUILDCONFIG_STI_IMAGE),
					asString(BUILDCONFIG_STI_SCRIPTS),
					asBoolean(BUILDCONFIG_STI_INCREMENTAL),
					getEnvMap(BUILDCONFIG_STI_ENV)
					);

		case Docker:
			return (T) new DockerBuildStrategy(
					asString(BUILDCONFIG_DOCKER_CONTEXTDIR),
					asBoolean(BUILDCONFIG_DOCKER_NOCACHE),
					asString(BUILDCONFIG_DOCKER_BASEIMAGE)
					);
		default:
		}
		return null;
	}
}
