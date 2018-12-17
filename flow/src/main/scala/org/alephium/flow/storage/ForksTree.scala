package org.alephium.flow.storage

import org.alephium.crypto.Keccak256
import org.alephium.flow.constant.Consensus
import org.alephium.protocol.model.{Block, Transaction}
import org.alephium.util.AVector

import scala.annotation.tailrec
import scala.collection.mutable.{ArrayBuffer, HashMap, HashSet}

class ForksTree(root: ForksTree.Root) extends SingleChain {

  private val blocksTable: HashMap[Keccak256, ForksTree.TreeNode] = HashMap.empty
  private val transactionsTable: HashMap[Keccak256, Transaction]  = HashMap.empty
  private val tips: HashSet[Keccak256]                            = HashSet.empty
  private val confirmedBlocks: ArrayBuffer[ForksTree.TreeNode]    = ArrayBuffer.empty

  // Initialization
  {
    postOrderTraverse(updateTable)
  }

  // Assuming that node is a tip node
  private def updateTable(node: ForksTree.TreeNode): Unit = {
    assert(node.isLeaf)
    val hash = node.block.hash

    blocksTable += hash -> node

    node.block.transactions.foreach { transaction =>
      transactionsTable += transaction.hash -> transaction
    }

    node match {
      case _: ForksTree.Root =>
        tips.add(hash)
        ()
      case n: ForksTree.Node =>
        tips.remove(n.parent.block.hash)
        tips.add(hash)
        ()
    }

    pruneDueto(node)
    confirmBlocks()

    ()
  }

  private def removeFromTable(node: ForksTree.TreeNode): Unit = {
    val hash = node.block.hash

    blocksTable.remove(hash)

    node.block.transactions.foreach { transaction =>
      transactionsTable.remove(transaction.hash)
    }

    if (tips.contains(hash)) tips.remove(hash)
    ()
  }

  private def pruneDueto(newNode: ForksTree.TreeNode): Boolean = {
    val toCut = tips.filter { key =>
      val tipNode = blocksTable(key)
      newNode.height >= tipNode.height + Consensus.blockConfirmNum
    }

    toCut.foreach { key =>
      val node = blocksTable(key)
      pruneBranchFrom(node)
    }
    toCut.nonEmpty
  }

  @tailrec
  private def pruneBranchFrom(node: ForksTree.TreeNode): Unit = {
    removeFromTable(node)

    node match {
      case n: ForksTree.Node =>
        val parent = n.parent
        if (parent.successors.size == 1) {
          pruneBranchFrom(parent)
        }
      case _: ForksTree.Root => ()
    }
  }

  private def confirmBlocks(): Unit = {
    val oldestTip = tips.view.map(blocksTable).minBy(_.height)

    @tailrec
    def iter(): Unit = {
      if (confirmedBlocks.isEmpty && root.successors.size == 1) {
        confirmedBlocks.append(root)
        iter()
      } else if (confirmedBlocks.nonEmpty) {
        val lastConfirmed = confirmedBlocks.last
        if (lastConfirmed.successors.size == 1 && (oldestTip.height >= lastConfirmed.height + Consensus.blockConfirmNum)) {
          confirmedBlocks.append(lastConfirmed.successors.head)
          iter()
        }
      }
    }

    iter()
  }

  private def postOrderTraverse(f: ForksTree.TreeNode => Unit): Unit = {
    def iter(node: ForksTree.TreeNode): Unit = {
      if (!node.isLeaf) node.successors.foreach(iter)
      f(node)
    }
    iter(root)
  }

  override def numBlocks: Int = blocksTable.size

  override def numTransactions: Int = transactionsTable.size

  override def maxHeight: Int = blocksTable.values.map(_.height).max

  override def maxWeight: Int = blocksTable.values.map(_.weight).max

  override def contains(hash: Keccak256): Boolean = blocksTable.contains(hash)

  override def add(block: Block, parentHash: Keccak256, weight: Int): AddBlockResult = {
    blocksTable.get(block.hash) match {
      case Some(_) => AddBlockResult.AlreadyExisted
      case None =>
        blocksTable.get(parentHash) match {
          case Some(parent) =>
            val newNode = ForksTree.Node(block, parent, parent.height + 1, weight)
            parent.successors += newNode
            updateTable(newNode)
            AddBlockResult.Success
          case None =>
            AddBlockResult.MissingDeps(AVector(parentHash))
        }
    }
  }

  override def getBlock(hash: Keccak256): Block = blocksTable(hash).block

  override def getConfirmedBlock(height: Int): Option[Block] = {
    if (height < confirmedBlocks.size && height >= 0) {
      Some(confirmedBlocks(height).block)
    } else None
  }

  override def getBlocks(locator: Keccak256): AVector[Block] = {
    blocksTable.get(locator) match {
      case Some(node) => getBlocksAfter(node)
      case None       => AVector.empty[Block]
    }
  }

  private def getBlocksAfter(node: ForksTree.TreeNode): AVector[Block] = {
    if (node.isLeaf) AVector.empty[Block]
    else {
      val buffer = node.successors.foldLeft(node.successors.map(_.block)) {
        case (blocks, successor) =>
          blocks ++ getBlocksAfter(successor).toIterable
      }
      AVector.from(buffer)
    }
  }

  override def getHeight(hash: Keccak256): Int = {
    assert(contains(hash))
    blocksTable(hash).height
  }

  override def getWeight(hash: Keccak256): Int = {
    assert(contains(hash))
    blocksTable(hash).weight
  }

  private def getChain(node: ForksTree.TreeNode): AVector[ForksTree.TreeNode] = {
    @tailrec
    def iter(acc: AVector[ForksTree.TreeNode],
             current: ForksTree.TreeNode): AVector[ForksTree.TreeNode] = {
      current match {
        case n: ForksTree.Root => acc :+ n
        case n: ForksTree.Node => iter(acc :+ current, n.parent)
      }
    }
    iter(AVector.empty, node).reverse
  }

  override def getBlockSlice(hash: Keccak256): AVector[Block] = {
    blocksTable.get(hash) match {
      case Some(node) =>
        getChain(node).map(_.block)
      case None =>
        AVector.empty
    }
  }

  override def isTip(hash: Keccak256): Boolean = {
    tips.contains(hash)
  }

  override def getBestTip: Keccak256 = {
    getAllTips.map(blocksTable.apply).maxBy(_.height).block.hash
  }

  override def getAllTips: AVector[Keccak256] = {
    AVector.from(tips)
  }

  override def getAllBlocks: Iterable[Block] = blocksTable.values.map(_.block)

  override def isBefore(hash1: Keccak256, hash2: Keccak256): Boolean = {
    assert(blocksTable.contains(hash1) && blocksTable.contains(hash2))
    val node1 = blocksTable(hash1)
    val node2 = blocksTable(hash2)
    isBefore(node1, node2)
  }

  private def getPredecessor(node: ForksTree.TreeNode, height: Int): ForksTree.TreeNode = {
    @tailrec
    def iter(current: ForksTree.TreeNode): ForksTree.TreeNode = {
      assert(current.height >= height && height >= root.height)
      current match {
        case n: ForksTree.Node =>
          if (n.height == height) {
            current
          } else {
            iter(n.parent)
          }
        case _: ForksTree.Root =>
          assert(height == root.height)
          current
      }
    }

    iter(node)
  }

  private def isBefore(node1: ForksTree.TreeNode, node2: ForksTree.TreeNode): Boolean = {
    val height1 = node1.height
    val height2 = node2.height
    if (height1 < height2) {
      val node1Infer = getPredecessor(node2, node1.height)
      node1Infer.eq(node1)
    } else if (height1 == height2) {
      node1.eq(node2)
    } else false
  }

  override def getTransaction(hash: Keccak256): Transaction = transactionsTable(hash)
}

object ForksTree {

  sealed trait TreeNode {
    val block: Block
    val successors: ArrayBuffer[Node]
    val height: Int
    val weight: Int

    def isRoot: Boolean
    def isLeaf: Boolean = successors.isEmpty
  }

  case class Root(
      block: Block,
      successors: ArrayBuffer[Node],
      height: Int,
      weight: Int
  ) extends TreeNode {
    override def isRoot: Boolean = true
  }

  object Root {
    def apply(block: Block, height: Int, weight: Int): Root =
      Root(block, ArrayBuffer.empty, height, weight)
  }

  case class Node(
      block: Block,
      parent: TreeNode,
      successors: ArrayBuffer[Node],
      height: Int,
      weight: Int
  ) extends TreeNode {
    def isRoot: Boolean = false
  }

  object Node {
    def apply(block: Block, parent: TreeNode, height: Int, weight: Int): Node = {
      new Node(block, parent, ArrayBuffer.empty, height, weight)
    }
  }

  def apply(genesis: Block): ForksTree = apply(genesis, 0, 0)

  def apply(genesis: Block, initialHeight: Int, initialWeight: Int): ForksTree = {
    val root = Root(genesis, initialHeight, initialWeight)
    new ForksTree(root)
  }
}