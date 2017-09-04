/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.net.simples;

import co.rsk.net.*;
import co.rsk.net.messages.Message;
import co.rsk.net.messages.StatusMessage;
import org.ethereum.core.Block;
import org.ethereum.crypto.HashUtil;

import java.math.BigInteger;

/**
 * Created by ajlopez on 5/14/2016.
 */
public class SimpleNode {
    private MessageHandler handler;
    private NodeID nodeID = new NodeID(HashUtil.randomPeerId());

    public SimpleNode(MessageHandler handler) {
        this.handler = handler;
    }

    public MessageHandler getHandler() {
        return this.handler;
    }

    public void sendMessage(SimpleNode sender, Message message) {
        this.processMessage(new SimpleNodeSender(this, sender, nodeID), message);
    }

    public Block getBestBlock() {
        return ((NodeMessageHandler)this.handler).getBestBlock();
    }

    public BigInteger getTotalDifficulty() {
        return ((NodeMessageHandler)this.handler).getTotalDifficulty();
    }

    public void sendStatus(SimpleNode node) {
        Block block = this.getBestBlock();
        Status status = new Status(block.getNumber(), block.getHash());
        node.sendMessage(this, new StatusMessage(status));
    }

    public void sendFullStatus(SimpleNode node) {
        Block block = this.getBestBlock();
        Status status = new Status(block.getNumber(), block.getHash(), block.getParentHash(), this.getTotalDifficulty());
        node.sendMessage(this, new StatusMessage(status));
    }

    protected void processMessage(MessageSender sender, Message message) {
        this.handler.processMessage(sender, message);
    }

    public NodeID getNodeID() { return nodeID; }
}
