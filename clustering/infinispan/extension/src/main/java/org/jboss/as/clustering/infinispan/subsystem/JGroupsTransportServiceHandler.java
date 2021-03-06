/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.clustering.infinispan.subsystem;

import static org.jboss.as.clustering.infinispan.subsystem.JGroupsTransportResourceDefinition.Attribute.*;

import java.util.ServiceLoader;

import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.dmr.ModelNodes;
import org.jboss.as.clustering.jgroups.subsystem.JGroupsBindingFactory;
import org.jboss.as.clustering.naming.BinderServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jgroups.Channel;
import org.wildfly.clustering.jgroups.spi.ChannelFactory;
import org.wildfly.clustering.jgroups.spi.service.ChannelBuilder;
import org.wildfly.clustering.jgroups.spi.service.ChannelServiceName;
import org.wildfly.clustering.jgroups.spi.service.ChannelServiceNameFactory;
import org.wildfly.clustering.jgroups.spi.service.ProtocolStackServiceName;
import org.wildfly.clustering.service.AliasServiceBuilder;
import org.wildfly.clustering.service.Builder;
import org.wildfly.clustering.service.GroupServiceNameFactory;
import org.wildfly.clustering.spi.GroupAliasBuilderProvider;

/**
 * @author Paul Ferraro
 */
public class JGroupsTransportServiceHandler implements ResourceServiceHandler {

    @Override
    public void installServices(OperationContext context, ModelNode model) throws OperationFailedException {
        String name = context.getCurrentAddress().getParent().getLastElement().getValue();
        ServiceTarget target = context.getServiceTarget();

        String channel = ModelNodes.asString(CHANNEL.getDefinition().resolveModelAttribute(context, model), GroupServiceNameFactory.DEFAULT_GROUP);

        new JGroupsTransportBuilder(name).configure(context, model).build(target).setInitialMode(ServiceController.Mode.PASSIVE).install();

        new SiteBuilder(name).configure(context, model).build(target).setInitialMode(ServiceController.Mode.PASSIVE).install();

        new BinderServiceBuilder<>(JGroupsBindingFactory.createChannelBinding(name), ChannelServiceName.CHANNEL.getServiceName(name), Channel.class).build(target).install();
        new ChannelBuilder(name).build(target).setInitialMode(ServiceController.Mode.PASSIVE).install();
        // Do not install a channel connector - the transport will perform connect/disconnect
        new AliasServiceBuilder<>(ChannelServiceName.FACTORY.getServiceName(name), ProtocolStackServiceName.CHANNEL_FACTORY.getServiceName(channel), ChannelFactory.class).build(target).setInitialMode(ServiceController.Mode.PASSIVE).install();

        for (GroupAliasBuilderProvider provider : ServiceLoader.load(GroupAliasBuilderProvider.class, GroupAliasBuilderProvider.class.getClassLoader())) {
            for (Builder<?> builder : provider.getBuilders(name, channel)) {
                builder.build(target).setInitialMode(ServiceController.Mode.ON_DEMAND).install();
            }
        }
    }

    @Override
    public void removeServices(OperationContext context, ModelNode model) throws OperationFailedException {
        String name = context.getCurrentAddress().getParent().getLastElement().getValue();

        String channel = ModelNodes.asString(CHANNEL.getDefinition().resolveModelAttribute(context, model), GroupServiceNameFactory.DEFAULT_GROUP);

        for (GroupAliasBuilderProvider provider : ServiceLoader.load(GroupAliasBuilderProvider.class, GroupAliasBuilderProvider.class.getClassLoader())) {
            for (Builder<?> builder : provider.getBuilders(name, channel)) {
                context.removeService(builder.getServiceName());
            }
        }

        for (ChannelServiceNameFactory factory : ChannelServiceName.values()) {
            context.removeService(factory.getServiceName(name));
        }

        for (CacheContainerComponent factory : CacheContainerComponent.values()) {
            context.removeService(factory.getServiceName(name));
        }
    }
}
