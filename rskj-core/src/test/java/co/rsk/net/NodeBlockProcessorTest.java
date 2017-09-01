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

package co.rsk.net;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.net.messages.*;
import co.rsk.net.simples.SimpleNodeSender;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.net.simples.SimpleMessageSender;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;

/**
 * Created by ajlopez on 5/11/2016.
 */
public class NodeBlockProcessorTest {
    @Test
    public void processBlockSavingInStore() {
        final BlockStore store = new BlockStore();
        final MessageSender sender = new SimpleMessageSender();

        final Blockchain blockchain = createBlockchain(0);
        final Block parent = BlockGenerator.createChildBlock(BlockGenerator.getGenesisBlock());
        final Block orphan = BlockGenerator.createChildBlock(parent);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        processor.processBlock(sender, orphan);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(orphan.getHash()).size() == 1);

        Assert.assertTrue(store.hasBlock(orphan));
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockWithTooMuchHeight() {
        final BlockStore store = new BlockStore();
        final MessageSender sender = new SimpleMessageSender();

        final Blockchain blockchain = createBlockchain(0);
        final Block orphan = BlockGenerator.createBlock(1000, 0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        processor.processBlock(sender, orphan);

        Assert.assertFalse(processor.getNodeInformation().getNodesByBlock(orphan.getHash()).size() == 1);
        Assert.assertFalse(store.hasBlock(orphan));
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processBlockWithTooMuchHeightAfterFilterIsRemoved() {
        final BlockStore store = new BlockStore();
        final MessageSender sender = new SimpleMessageSender();

        final Blockchain blockchain = createBlockchain(0);
        final Block block = BlockGenerator.createBlock(1000, 0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);
        processor.acceptAnyBlock();

        processor.processBlock(sender, block);

        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);
        Assert.assertTrue(store.hasBlock(block));
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockAddingToBlockchain() {
        Blockchain blockchain = createBlockchain(10);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());

        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBlockByNumber(0);
        store.saveBlock(genesis);
        Block block = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));

        Assert.assertEquals(11, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), block.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        processor.processBlock(null, block);

        Assert.assertFalse(store.hasBlock(block));
        Assert.assertEquals(11, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processTenBlocksAddingToBlockchain() {
        Blockchain blockchain = createBlockchain();
        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBestBlock();

        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        processor.processBlock(null, genesis);
        Assert.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTwoBlockListsAddingToBlockchain() {
        Blockchain blockchain = createBlockchain();
        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);
        List<Block> blocks2 = BlockGenerator.getBlockChain(genesis, 20);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        processor.processBlock(null, genesis);
        Assert.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);
        for (Block b : blocks2)
            processor.processBlock(null, b);

        Assert.assertEquals(20, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTwoBlockListsAddingToBlockchainWithFork() {
        BlockStore store = new BlockStore();
        Blockchain blockchain = createBlockchain();
        Block genesis = blockchain.getBestBlock();

        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);
        List<Block> blocks2 = BlockGenerator.getBlockChain(blocks.get(4), 20);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        processor.processBlock(null, genesis);
        Assert.assertEquals(0, store.size());

        for (Block b : blocks)
            processor.processBlock(null, b);
        for (Block b : blocks2)
            processor.processBlock(null, b);

        Assert.assertEquals(25, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void noSyncingWithEmptyBlockchain() {
        BlockStore store = new BlockStore();
        Block genesis = BlockGenerator.getGenesisBlock();
        Blockchain blockchain = createBlockchain();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        Assert.assertFalse(processor.isSyncingBlocks());
    }

    @Test
    public void noSyncingWithEmptyBlockchainAndLowBestBlock() {
        BlockStore store = new BlockStore();
        Block block = BlockGenerator.createBlock(10, 0);
        Blockchain blockchain = createBlockchain();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        Assert.assertFalse(processor.isSyncingBlocks());

        Status status = new Status(block.getNumber(), block.getHash());
        processor.processStatus(new SimpleNodeSender(null, null), status);

        Assert.assertFalse(processor.isSyncingBlocks());
    }

    @Test
    public void syncingWithEmptyBlockchainAndHighBestBlock() {
        BlockStore store = new BlockStore();
        Block block = BlockGenerator.createBlock(30, 0);
        Blockchain blockchain = createBlockchain();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        Assert.assertFalse(processor.isSyncingBlocks());

        Status status = new Status(block.getNumber(), block.getHash());
        processor.processStatus(new SimpleNodeSender(null, null), status);

        Assert.assertTrue(processor.isSyncingBlocks());
    }

    @Test
    public void syncingThenNoSyncing() {
        BlockStore store = new BlockStore();
        Block block = BlockGenerator.createBlock(30, 0);
        Blockchain blockchain = createBlockchain();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        Assert.assertFalse(processor.isSyncingBlocks());

        Status status = new Status(block.getNumber(), block.getHash());
        processor.processStatus(new SimpleNodeSender(null, null), status);

        Assert.assertTrue(processor.hasBetterBlockToSync());
        Assert.assertTrue(processor.isSyncingBlocks());

        blockchain.setBestBlock(block);
        blockchain.setTotalDifficulty(BigInteger.valueOf(30));

        Assert.assertFalse(processor.hasBetterBlockToSync());
        Assert.assertFalse(processor.isSyncingBlocks());

        Block block2 = BlockGenerator.createBlock(60, 0);
        Status status2 = new Status(block2.getNumber(), block2.getHash());
        processor.processStatus(new SimpleNodeSender(null, null), status2);

        Assert.assertTrue(processor.hasBetterBlockToSync());
        Assert.assertFalse(processor.isSyncingBlocks());
    }

    @Test
    public void processTenBlocksGenesisAtLastAddingToBlockchain() {
        BlockStore store = new BlockStore();
        Blockchain blockchain = createBlockchain();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        for (Block b : blocks)
            processor.processBlock(null, b);

        processor.processBlock(null, genesis);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTenBlocksInverseOrderAddingToBlockchain() {
        Blockchain blockchain = createBlockchain();
        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        for (int k = 0; k < 10; k++)
            processor.processBlock(null, blocks.get(9 - k));

        processor.processBlock(null, genesis);

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processTenBlocksWithHoleAddingToBlockchain() {
        Blockchain blockchain = createBlockchain();
        BlockStore store = new BlockStore();
        Block genesis = blockchain.getBestBlock();
        List<Block> blocks = BlockGenerator.getBlockChain(genesis, 10);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        for (int k = 0; k < 10; k++)
            if (k != 5)
                processor.processBlock(null, blocks.get(9 - k));

        processor.processBlock(null, genesis);
        processor.processBlock(null, blocks.get(4));

        Assert.assertEquals(10, blockchain.getBestBlock().getNumber());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processBlockAddingToBlockchainUsingItsParent() {
        BlockStore store = new BlockStore();
        Block genesis = BlockGenerator.getGenesisBlock();
        store.saveBlock(genesis);
        Blockchain blockchain = createBlockchain(10);
        Block parent = BlockGenerator.createChildBlock(blockchain.getBlockByNumber(10));
        Block block = BlockGenerator.createChildBlock(parent);

        Assert.assertEquals(11, parent.getNumber());
        Assert.assertEquals(12, block.getNumber());
        Assert.assertArrayEquals(blockchain.getBestBlockHash(), parent.getParentHash());

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        processor.processBlock(null, block);

        Assert.assertTrue(store.hasBlock(block));
        Assert.assertNull(blockchain.getBlockByHash(block.getHash()));

        processor.processBlock(null, parent);

        Assert.assertFalse(store.hasBlock(block));
        Assert.assertFalse(store.hasBlock(parent));

        Assert.assertEquals(12, blockchain.getBestBlock().getNumber());
        Assert.assertArrayEquals(block.getHash(), blockchain.getBestBlockHash());
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processBlockRetrievingParentUsingSender() {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);
        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block genesis = BlockGenerator.getGenesisBlock();
        final Block parent = BlockGenerator.createChildBlock(genesis);
        final Block block = BlockGenerator.createChildBlock(parent);

        processor.processBlock(sender, block);

        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);
        Assert.assertTrue(store.hasBlock(block));
        Assert.assertEquals(1, sender.getMessages().size());
        Assert.assertEquals(1, store.size());

        final Message message = sender.getMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assert.assertArrayEquals(block.getParentHash(), gbMessage.getBlockHash());
    }

    @Test
    public void processStatusRetrievingBestBlockUsingSender() {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);
        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block genesis = BlockGenerator.getGenesisBlock();
        final Block block = BlockGenerator.createChildBlock(genesis);
        final Status status = new Status(block.getNumber(), block.getHash());

        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);

        Assert.assertEquals(1, sender.getGetBlockMessages().size());

        final Message message = sender.getGetBlockMessages().get(0);

        Assert.assertNotNull(message);
        Assert.assertEquals(MessageType.GET_BLOCK_MESSAGE, message.getMessageType());

        final GetBlockMessage gbMessage = (GetBlockMessage) message;

        Assert.assertArrayEquals(block.getHash(), gbMessage.getBlockHash());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processStatusHavingBestBlockInStore() {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);
        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block genesis = BlockGenerator.getGenesisBlock();
        final Block block = BlockGenerator.createChildBlock(genesis);

        store.saveBlock(block);
        final Status status = new Status(block.getNumber(), block.getHash());

        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);
        Assert.assertEquals(1, store.size());
    }

    @Test
    public void processStatusHavingBestBlockAsBestBlockInBlockchain() {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(2);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block block = blockchain.getBestBlock();
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        final Status status = new Status(block.getNumber(), block.getHash());

        processor.processStatus(sender, status);
        Assert.assertTrue(processor.getNodeInformation().getNodesByBlock(block.getHash()).size() == 1);
        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
        Assert.assertEquals(0, store.size());
    }

    @Test
    public void processStatusHavingBestBlockInBlockchainStore() {
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(2);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        final Block block = blockchain.getBlockByNumber(1);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        store.saveBlock(block);
        final Status status = new Status(block.getNumber(), block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processStatus(sender, status);
        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertEquals(0, sender.getGetBlockMessages().size());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInStore() {
        final Block block = BlockGenerator.getBlock(3);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        final BlockStore store = new BlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = createBlockchain(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processGetBlockHeaders(sender, block.getHash());

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, message.getMessageType());

        final BlockHeadersMessage bMessage = (BlockHeadersMessage) message;

        Assert.assertArrayEquals(block.getHeader().getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processGetBlockHeaderMessageUsingEmptyStore() {
        final Block block = BlockGenerator.getBlock(3);
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processGetBlockHeaders(sender, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockHeaderMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = createBlockchain(10);
        final Block block = blockchain.getBlockByNumber(5);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processGetBlockHeaders(sender, block.getHash());

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_MESSAGE, message.getMessageType());

        final BlockHeadersMessage bMessage = (BlockHeadersMessage) message;

        Assert.assertArrayEquals(block.getHeader().getHash(), bMessage.getBlockHeaders().get(0).getHash());
    }

    @Test
    public void processGetBlockMessageUsingBlockInStore() {
        final Block block = BlockGenerator.getBlock(3);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        final BlockStore store = new BlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = createBlockchain(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processGetBlock(sender, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assert.assertArrayEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processGetBlockMessageUsingEmptyStore() {
        final Block block = BlockGenerator.getBlock(3);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processGetBlock(sender, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processGetBlockMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = createBlockchain(10);
        final Block block = blockchain.getBlockByNumber(5);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processGetBlock(sender, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());

        final BlockMessage bMessage = (BlockMessage) message;

        Assert.assertArrayEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processBlockRequestMessageUsingBlockInStore() {
        final Block block = BlockGenerator.getBlock(3);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());

        final BlockStore store = new BlockStore();
        store.saveBlock(block);

        final Blockchain blockchain = createBlockchain(0);
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, message.getMessageType());

        final BlockResponseMessage bMessage = (BlockResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());
        Assert.assertArrayEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processBodyRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = createBlockchain(10);
        final Block block = blockchain.getBlockByNumber(3);
        final BlockStore store = new BlockStore();
        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processBodyRequest(sender, 100, block.getHash());

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BODY_RESPONSE_MESSAGE, message.getMessageType());

        final BodyResponseMessage bMessage = (BodyResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());
        Assert.assertEquals(block.getTransactionsList(), bMessage.getTransactions());
        Assert.assertEquals(block.getUncleList(), bMessage.getUncles());
    }

    @Test
    public void processBlockHashRequestMessageUsingEmptyStore() {
        final Block block = BlockGenerator.getBlock(3);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());
        final BlockStore store = new BlockStore();
        final Blockchain blockchain = createBlockchain(0);

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash());

        Assert.assertFalse(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processBlockHashRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = createBlockchain(10);
        final Block block = blockchain.getBlockByNumber(5);
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(block.getHash());
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).isEmpty());

        processor.processBlockRequest(sender, 100, block.getHash());

        Assert.assertTrue(nodeInformation.getBlocksByNode(sender.getNodeID()).contains(blockHash));

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_RESPONSE_MESSAGE, message.getMessageType());

        final BlockResponseMessage bMessage = (BlockResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());
        Assert.assertArrayEquals(block.getHash(), bMessage.getBlock().getHash());
    }

    @Test
    public void processBlockHeadersRequestMessageUsingBlockInBlockchain() {
        final Blockchain blockchain = createBlockchain(100);
        final Block block = blockchain.getBlockByNumber(60);
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processBlockHeadersRequest(sender, 100, block.getHash(), 20);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.BLOCK_HEADERS_RESPONSE_MESSAGE, message.getMessageType());

        final BlockHeadersResponseMessage response = (BlockHeadersResponseMessage) message;

        Assert.assertEquals(100, response.getId());
        Assert.assertNotNull(response.getBlockHeaders());
        Assert.assertEquals(20, response.getBlockHeaders().size());

        for (int k = 0; k < 20; k++)
            Assert.assertArrayEquals(blockchain.getBlockByNumber(60 - k).getHash(), response.getBlockHeaders().get(k).getHash());
    }

    @Test
    public void processBlockHeadersRequestMessageUsingUnknownHash() {
        final Blockchain blockchain = createBlockchain(100);
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processBlockHeadersRequest(sender, 100, HashUtil.randomHash(), 20);

        Assert.assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    public void processSkeletonRequestWithGenesisPlusBestBlockInSkeleton() {
        int skeletonStep = 192;
        final Blockchain blockchain = createBlockchain(skeletonStep / 2);
        final Block blockStart = blockchain.getBlockByNumber(5);
        final Block blockEnd = blockchain.getBlockByNumber(skeletonStep / 2);
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processSkeletonRequest(sender, 100, 5);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());

        Block genesis = blockchain.getBlockByNumber(0);
        Block bestBlock = blockchain.getBestBlock();
        BlockIdentifier[] expected = {
                new BlockIdentifier(genesis.getHash(), genesis.getNumber()),
                new BlockIdentifier(bestBlock.getHash(), bestBlock.getNumber()),
        };
        assertBlockIdentifiers(expected, bMessage.getBlockIdentifiers());
    }

    @Test
    public void processSkeletonRequestWithThreeResults() {
        int skeletonStep = 192;
        final Blockchain blockchain = createBlockchain(300);
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processSkeletonRequest(sender, 100, 5);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());

        Block b1 = blockchain.getBlockByNumber(0);
        Block b2 = blockchain.getBlockByNumber(skeletonStep);
        Block b3 = blockchain.getBestBlock();
        BlockIdentifier[] expected = {
                new BlockIdentifier(b1.getHash(), b1.getNumber()),
                new BlockIdentifier(b2.getHash(), b2.getNumber()),
                new BlockIdentifier(b3.getHash(), b3.getNumber()),
        };
        assertBlockIdentifiers(expected, bMessage.getBlockIdentifiers());
    }

    @Test
    public void processSkeletonRequestNotIncludingGenesis() {
        int skeletonStep = 192;
        final Blockchain blockchain = createBlockchain(400);
        final BlockStore store = new BlockStore();

        BlockNodeInformation nodeInformation = new BlockNodeInformation();
        BlockSyncService blockSyncService = new BlockSyncService(store, blockchain, nodeInformation, null);
        final NodeBlockProcessor processor = new NodeBlockProcessor(store, blockchain, nodeInformation, blockSyncService);

        final SimpleMessageSender sender = new SimpleMessageSender();

        processor.processSkeletonRequest(sender, 100, skeletonStep + 5);

        Assert.assertFalse(sender.getMessages().isEmpty());
        Assert.assertEquals(1, sender.getMessages().size());

        final Message message = sender.getMessages().get(0);

        Assert.assertEquals(MessageType.SKELETON_RESPONSE_MESSAGE, message.getMessageType());

        final SkeletonResponseMessage bMessage = (SkeletonResponseMessage) message;

        Assert.assertEquals(100, bMessage.getId());

        Block b1 = blockchain.getBlockByNumber(skeletonStep);
        Block b2 = blockchain.getBlockByNumber(2 * skeletonStep);
        Block b3 = blockchain.getBestBlock();
        BlockIdentifier[] expected = {
                new BlockIdentifier(b1.getHash(), b1.getNumber()),
                new BlockIdentifier(b2.getHash(), b2.getNumber()),
                new BlockIdentifier(b3.getHash(), b3.getNumber()),
        };
        assertBlockIdentifiers(expected, bMessage.getBlockIdentifiers());
    }

    private static void assertBlockIdentifiers(BlockIdentifier[] expected, List<BlockIdentifier> actual) {
        Assert.assertEquals(expected.length, actual.size());

        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i].getNumber(), actual.get(i).getNumber());
            Assert.assertArrayEquals(expected[i].getHash(), actual.get(i).getHash());
        }
    }

    private static Blockchain createBlockchain() {
        return createBlockchain(0);
    }

    private static Blockchain createBlockchain(int size) {
        BlockChainBuilder builder = new BlockChainBuilder();
        BlockChainImpl blockChain = builder.build();

        Block genesis = BlockGenerator.getGenesisBlock();
        genesis.setStateRoot(blockChain.getRepository().getRoot());
        genesis.flushRLP();

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockChain.tryToConnect(genesis));

        if (size > 0) {
            List<Block> blocks = BlockGenerator.getBlockChain(genesis, size);

            for (Block block: blocks)
                blockChain.tryToConnect(block);
        }

        return blockChain;
    }
}