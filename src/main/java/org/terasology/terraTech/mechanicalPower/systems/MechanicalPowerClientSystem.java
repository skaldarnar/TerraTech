/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.terraTech.mechanicalPower.systems;

import org.terasology.blockNetwork.Network;
import org.terasology.blockNetwork.NetworkNode;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.OnAddedComponent;
import org.terasology.entitySystem.entity.lifecycleEvents.OnChangedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.itemRendering.components.AnimateRotationComponent;
import org.terasology.itemRendering.components.RenderItemTransformComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.Pitch;
import org.terasology.math.Roll;
import org.terasology.math.Rotation;
import org.terasology.math.Side;
import org.terasology.math.Yaw;
import org.terasology.registry.In;
import org.terasology.terraTech.mechanicalPower.components.MechanicalPowerConsumerComponent;
import org.terasology.terraTech.mechanicalPower.components.MechanicalPowerProducerComponent;
import org.terasology.terraTech.mechanicalPower.components.RotatingAxleComponent;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.items.BlockItemComponent;

@RegisterSystem(RegisterMode.CLIENT)
public class MechanicalPowerClientSystem extends BaseComponentSystem {
    @In
    BlockEntityRegistry blockEntityRegistry;
    @In
    EntityManager entityManager;
    @In
    MechanicalPowerNetwork mechanicalPowerNetwork;

    @ReceiveEvent
    public void createRenderedAxle(OnAddedComponent event, EntityRef entity, RotatingAxleComponent rotatingAxle, LocationComponent location, BlockComponent block) {
        EntityBuilder renderedEntityBuilder = entityManager.newBuilder("RotatingAxle");
        renderedEntityBuilder.setOwner(entity);
        // set the look of the rendered entity
        BlockItemComponent blockItem = renderedEntityBuilder.getComponent(BlockItemComponent.class);
        blockItem.blockFamily = rotatingAxle.renderedBlockFamily;
        renderedEntityBuilder.saveComponent(blockItem);

        // rotate the rendered entity to match the block
        RenderItemTransformComponent renderItemTransform = renderedEntityBuilder.getComponent(RenderItemTransformComponent.class);
        Side direction = block.getBlock().getDirection();
        Rotation rotation = getRotation(direction);
        renderItemTransform.pitch = rotation.getPitch();
        renderItemTransform.roll = rotation.getRoll();
        renderItemTransform.yaw = rotation.getYaw();
        renderedEntityBuilder.saveComponent(renderItemTransform);

        rotatingAxle.renderedEntity = renderedEntityBuilder.build();
        entity.saveComponent(rotatingAxle);
    }


    public Rotation getRotation(Side side) {
        Pitch pitch = Pitch.NONE;
        Yaw yaw = Yaw.NONE;

        if (side == Side.BACK) {
            pitch = Pitch.CLOCKWISE_180;
        } else if (side == Side.TOP) {
            pitch = Pitch.CLOCKWISE_90;
        } else if (side == Side.BOTTOM) {
            pitch = pitch.CLOCKWISE_270;
        } else if (side == Side.LEFT) {
            yaw = Yaw.CLOCKWISE_90;
        } else if (side == Side.RIGHT) {
            yaw = Yaw.CLOCKWISE_270;
        }

        return Rotation.rotate(yaw, pitch);
    }

    @ReceiveEvent
    public void updateAxlesInNetwork(OnChangedComponent event, EntityRef entity, MechanicalPowerProducerComponent powerProducer, BlockComponent block) {
        boolean isPowerNetworkActive = false;
        int powerConsumers = 1;
        float totalPower = 0;
        Network network = mechanicalPowerNetwork.getNetwork(block.getPosition());

        for (NetworkNode node : mechanicalPowerNetwork.getLeafNodes().get(network)) {
            EntityRef nodeEntity = blockEntityRegistry.getBlockEntityAt(node.location.toVector3i());

            if (nodeEntity.hasComponent(MechanicalPowerConsumerComponent.class)) {
                powerConsumers++;
            }

            MechanicalPowerProducerComponent nodePowerProducer = nodeEntity.getComponent(MechanicalPowerProducerComponent.class);
            if (nodePowerProducer != null) {
                if (nodePowerProducer.active) {
                    isPowerNetworkActive = true;
                    totalPower += nodePowerProducer.power;
                }
            }
        }

        float speed = totalPower / powerConsumers;

        for (NetworkNode node : network.getNetworkingNodes()) {
            EntityRef nodeEntity = blockEntityRegistry.getBlockEntityAt(node.location.toVector3i());

            RotatingAxleComponent rotatingAxle = nodeEntity.getComponent(RotatingAxleComponent.class);
            if (rotatingAxle != null) {
                if (isPowerNetworkActive) {
                    // ensure all axle rotation is turned on
                    turnAxleOn(rotatingAxle.renderedEntity, speed);
                } else {
                    // ensure all axle rotation is turned off
                    turnAxleOff(rotatingAxle.renderedEntity);
                }
            }
        }
    }

    private void turnAxleOn(EntityRef renderedEntity, float speed) {
        AnimateRotationComponent animateRotation = renderedEntity.getComponent(AnimateRotationComponent.class);
        if (animateRotation != null) {
            // update the speed if we have already added this component
            if (animateRotation.speed != speed) {
                animateRotation.speed = speed;
                renderedEntity.saveComponent(animateRotation);
            }
        } else {
            Rotation targetRotation = Rotation.rotate(Roll.CLOCKWISE_90);
            RenderItemTransformComponent renderItemTransform = renderedEntity.getComponent(RenderItemTransformComponent.class);
            if (renderItemTransform.yaw != Yaw.NONE) {
                targetRotation = Rotation.rotate(Pitch.CLOCKWISE_90);
            } else if (renderItemTransform.pitch != Pitch.NONE) {
                targetRotation = Rotation.rotate(Yaw.CLOCKWISE_90);
            } else if (renderItemTransform.roll != Roll.NONE) {
                targetRotation = Rotation.rotate(Roll.CLOCKWISE_90);
            }

            animateRotation = new AnimateRotationComponent();
            animateRotation.pitch = targetRotation.getPitch();
            animateRotation.roll = targetRotation.getRoll();
            animateRotation.yaw = targetRotation.getYaw();
            animateRotation.speed = speed;
            renderedEntity.addComponent(animateRotation);
        }
    }

    private void turnAxleOff(EntityRef renderedEntity) {
        renderedEntity.removeComponent(AnimateRotationComponent.class);
    }
}