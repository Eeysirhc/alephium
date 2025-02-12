// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.api

import java.net.{InetAddress, InetSocketAddress}

import akka.util.ByteString
import org.scalacheck.Gen
import org.scalatest.EitherValues

import org.alephium.api.{model => api}
import org.alephium.api.UtilJson._
import org.alephium.api.model._
import org.alephium.json.Json._
import org.alephium.protocol._
import org.alephium.protocol.model.{AssetOutput => _, ContractOutput => _, _}
import org.alephium.protocol.vm.{GasBox, GasPrice, LockupScript, StatefulContract}
import org.alephium.protocol.vm.lang.TypeSignatureFixture
import org.alephium.util._
import org.alephium.util.Hex.HexStringSyntax

//scalastyle:off file.size.limit
class ApiModelSpec extends JsonFixture with ApiModelFixture with EitherValues with NumericHelpers {
  val defaultUtxosLimit: Int = 1024

  val zeroHash: String = BlockHash.zero.toHexString
  def entryDummy(i: Int): BlockEntry =
    BlockEntry(
      BlockHash.zero,
      TimeStamp.unsafe(i.toLong),
      i,
      i,
      i,
      AVector(BlockHash.zero),
      AVector.empty,
      ByteString.empty,
      1.toByte,
      Hash.zero,
      Hash.zero,
      ByteString.empty
    )
  val dummyAddress = new InetSocketAddress("127.0.0.1", 9000)
  val dummyCliqueInfo =
    CliqueInfo.unsafe(
      CliqueId.generate,
      AVector(Option(dummyAddress)),
      AVector(dummyAddress),
      1,
      priKey
    )
  val dummyPeerInfo = BrokerInfo.unsafe(CliqueId.generate, 1, 3, dummyAddress)

  val apiKey = Hash.generate.toHexString

  val inetAddress = InetAddress.getByName("127.0.0.1")

  def generateAddress(): Address.Asset = Address.p2pkh(PublicKey.generate)
  def generateContractAddress(): Address.Contract =
    Address.Contract(LockupScript.p2c("uomjgUz6D4tLejTkQtbNJMY8apAjTm1bgQf7em1wDV7S").get)

  def blockEntryJson(blockEntry: BlockEntry): String = {
    s"""
       |{
       |  "hash":"${blockEntry.hash.toHexString}",
       |  "timestamp":${blockEntry.timestamp.millis},
       |  "chainFrom":${blockEntry.chainFrom},
       |  "chainTo":${blockEntry.chainTo},
       |  "height":${blockEntry.height},
       |  "deps":${write(blockEntry.deps.map(_.toHexString))},
       |  "transactions":${write(blockEntry.transactions)},
       |  "nonce":"${Hex.toHexString(blockEntry.nonce)}",
       |  "version":${blockEntry.version},
       |  "depStateHash":"${blockEntry.depStateHash.toHexString}",
       |  "txsHash":"${blockEntry.txsHash.toHexString}",
       |  "target":"${Hex.toHexString(blockEntry.target)}"
       |}""".stripMargin
  }
  def parseFail[A: Reader](jsonRaw: String): String = {
    scala.util.Try(read[A](jsonRaw)).toEither.swap.rightValue.getMessage
  }

  it should "encode/decode TimeStamp" in {
    checkData(TimeStamp.unsafe(0), "0")
    checkData(TimeStamp.unsafe(43850028L), "43850028")
    checkData(TimeStamp.unsafe(4385002872679507624L), "\"4385002872679507624\"")

    forAll(negLongGen) { long =>
      parseFail[TimeStamp](s"$long") is "expect positive timestamp at index 0"
    }
  }

  it should "encode/decode Amount.Hint" in {
    checkData(Amount.Hint(ALPH.oneAlph), """"1 ALPH"""", dropWhiteSpace = false)
    read[Amount.Hint](""""1000000000000000000"""") is Amount.Hint(ALPH.oneAlph)

    val alph = ALPH.alph(1234) / 1000
    checkData(Amount.Hint(alph), """"1.234 ALPH"""", dropWhiteSpace = false)
    read[Amount.Hint](""""1234000000000000000"""") is Amount.Hint(alph)

    val small = ALPH.alph(1234) / 1000000000
    checkData(Amount.Hint(small), """"0.000001234 ALPH"""", dropWhiteSpace = false)
    read[Amount.Hint](""""1234000000000"""") is Amount.Hint(small)

    parseFail[Amount.Hint](""""1 alph"""")
  }

  it should "encode/decode empty FetchResponse" in {
    val response = FetchResponse(AVector.empty)
    val jsonRaw =
      """{"blocks":[]}"""
    checkData(response, jsonRaw)
  }

  it should "encode/decode FetchResponse" in {
    val entries  = AVector.tabulate(2)(entryDummy)
    val response = FetchResponse(AVector(entries))
    val jsonRaw =
      s"""{"blocks":[[${blockEntryJson(entries.head)},${blockEntryJson(entries.last)}]]}"""
    checkData(response, jsonRaw)
  }

  it should "encode/decode NodeInfo" in {
    val nodeInfo =
      NodeInfo(
        NodeInfo.BuildInfo("1.2.3", "07b7f3e044"),
        true,
        Some(dummyAddress)
      )
    val jsonRaw = {
      s"""
         |{
         |  "buildInfo": { "releaseVersion": "1.2.3", "commit": "07b7f3e044" },
         |  "upnp": true,
         |  "externalAddress": { "addr": "127.0.0.1", "port": 9000 }
         |}""".stripMargin
    }
    checkData(nodeInfo, jsonRaw)
  }

  it should "encode/decode NodeVersion" in {
    val nodeVersion = NodeVersion(ReleaseVersion(0, 0, 0))
    val jsonRaw =
      s"""
         |{
         |  "version": "v0.0.0"
         |}""".stripMargin
    checkData(nodeVersion, jsonRaw)
  }

  it should "encode/decode ChainParams" in {
    val chainParams = ChainParams(NetworkId.AlephiumMainNet, 18, 1, 2)
    val jsonRaw =
      s"""
         |{
         |  "networkId": 0,
         |  "numZerosAtLeastInHash": 18,
         |  "groupNumPerBroker": 1,
         |  "groups": 2
         |}""".stripMargin
    checkData(chainParams, jsonRaw)
  }

  it should "encode/decode SelfClique" in {
    val cliqueId = CliqueId.generate
    val peerAddress =
      PeerAddress(inetAddress, 9001, 9002, 9003)
    val selfClique =
      SelfClique(cliqueId, AVector(peerAddress), true, false)
    val jsonRaw =
      s"""
         |{
         |  "cliqueId": "${cliqueId.toHexString}",
         |  "nodes": [{"address":"127.0.0.1","restPort":9001,"wsPort":9002,"minerApiPort":9003}],
         |  "selfReady": true,
         |  "synced": false
         |}""".stripMargin
    checkData(selfClique, jsonRaw)
  }

  it should "encode/decode NeighborPeers" in {
    val neighborCliques = NeighborPeers(AVector(dummyPeerInfo))
    val cliqueIdString  = dummyPeerInfo.cliqueId.toHexString
    def jsonRaw(cliqueId: String) =
      s"""{"peers":[{"cliqueId":"$cliqueId","brokerId":1,"brokerNum":3,"address":{"addr":"127.0.0.1","port":9000}}]}"""
    checkData(neighborCliques, jsonRaw(cliqueIdString))

    parseFail[NeighborPeers](jsonRaw("OOPS")) is "invalid clique id at index 98"
  }

  it should "encode/decode GetBalance" in {
    val address    = generateAddress()
    val addressStr = address.toBase58
    val request    = GetBalance(address)
    val jsonRaw    = s"""{"address":"$addressStr"}"""
    checkData(request, jsonRaw)
  }

  it should "encode/decode AssetInput" in {
    val key       = Hash.generate
    val outputRef = OutputRef(1234, key)
    val data      = AssetInput(outputRef, hex"abcd")
    val jsonRaw =
      s"""{"outputRef":{"hint":1234,"key":"${key.toHexString}"},"unlockScript":"abcd"}"""
    checkData(data, jsonRaw)
  }

  it should "encode/decode Token" in {
    val id     = Hash.generate
    val amount = ALPH.oneAlph

    val token: Token = Token(id, amount)
    val jsonRaw =
      s"""{"id":"${id.toHexString}","amount":"${amount}"}"""

    checkData(token, jsonRaw)

    parseFail[Token](s"""{"id":"${id.toHexString}","amount":"1 ALPH"}""")
  }

  it should "encode/decode Output with big amount" in {
    val amount    = Amount(U256.unsafe(15).mulUnsafe(U256.unsafe(Number.quintillion)))
    val amountStr = "15000000000000000000"
    val tokenId1  = Hash.hash("token1")
    val tokenId2  = Hash.hash("token2")
    val tokens =
      AVector(Token(tokenId1, U256.unsafe(42)), Token(tokenId2, U256.unsafe(1000)))
    val hint = 1234
    val key  = hashGen.sample.get

    {
      val address         = generateContractAddress()
      val addressStr      = address.toBase58
      val request: Output = ContractOutput(hint, key, amount, address, tokens)
      val jsonRaw = s"""
                       |{
                       |  "type": "ContractOutput",
                       |  "hint": $hint,
                       |  "key": "${key.toHexString}",
                       |  "attoAlphAmount": "$amountStr",
                       |  "address": "$addressStr",
                       |  "tokens": [
                       |    {
                       |      "id": "${tokenId1.toHexString}",
                       |      "amount": "42"
                       |    },
                       |    {
                       |      "id": "${tokenId2.toHexString}",
                       |      "amount": "1000"
                       |    }
                       |  ]
                       |}
        """.stripMargin
      checkData(request, jsonRaw)
    }

    {
      val address    = generateAddress()
      val addressStr = address.toBase58
      val request: Output =
        AssetOutput(
          hint,
          key,
          amount,
          address,
          AVector.empty,
          TimeStamp.unsafe(1234),
          ByteString.empty
        )
      val jsonRaw = s"""
                       |{
                       |  "type": "AssetOutput",
                       |  "hint": $hint,
                       |  "key": "${key.toHexString}",
                       |  "attoAlphAmount": "$amountStr",
                       |  "address": "$addressStr",
                       |  "tokens": [],
                       |  "lockTime": 1234,
                       |  "message": ""
                       |}
        """.stripMargin
      checkData(request, jsonRaw)
    }
  }

  it should "encode/decode GetGroup" in {
    val address    = generateAddress()
    val addressStr = address.toBase58
    val request    = GetGroup(address)
    val jsonRaw    = s"""{"address":"$addressStr"}"""
    checkData(request, jsonRaw)
  }

  it should "encode/decode Balance" in {
    val amount = Amount(ALPH.alph(100))
    val locked = Amount(ALPH.alph(50))

    {
      info("with token balances")
      val tokenId1 = Hash.hash("token1")
      val tokenId2 = Hash.hash("token2")
      val tokens =
        AVector(Token(tokenId1, U256.unsafe(42)), Token(tokenId2, U256.unsafe(1000)))
      val tokenId3     = Hash.hash("token3")
      val lockedTokens = AVector(Token(tokenId3, U256.unsafe(1)))
      val response =
        Balance(amount, amount.hint, locked, locked.hint, Some(tokens), Some(lockedTokens), 1)
      val jsonRaw =
        s"""{"balance":"100000000000000000000","balanceHint":"100 ALPH","lockedBalance":"50000000000000000000","lockedBalanceHint":"50 ALPH","tokenBalances":[{"id":"${tokenId1.toHexString}","amount":"42"},{"id":"${tokenId2.toHexString}","amount":"1000"}],"lockedTokenBalances":[{"id":"${tokenId3.toHexString}","amount":"1"}],"utxoNum":1}"""
      checkData(response, jsonRaw, dropWhiteSpace = false)
    }

    {
      info("without token balances")
      val response = Balance(amount, amount.hint, locked, locked.hint, None, None, 1)
      val jsonRaw =
        s"""{"balance":"100000000000000000000","balanceHint":"100 ALPH","lockedBalance":"50000000000000000000","lockedBalanceHint":"50 ALPH","utxoNum":1}"""
      checkData(response, jsonRaw, dropWhiteSpace = false)
    }
  }

  it should "encode/decode Group" in {
    val response = Group(42)
    val jsonRaw  = """{"group":42}"""
    checkData(response, jsonRaw)
  }

  it should "encode/decode TxResult" in {
    val hash    = Hash.generate
    val result  = SubmitTxResult(hash, 0, 1)
    val jsonRaw = s"""{"txId":"${hash.toHexString}","fromGroup":0,"toGroup":1}"""
    checkData(result, jsonRaw)
  }

  it should "encode/decode BuildTransaction" in {
    val fromPublicKey = PublicKey.generate
    val toKey         = PublicKey.generate
    val toAddress     = Address.p2pkh(toKey)

    {
      val transfer =
        BuildTransaction(fromPublicKey, AVector(Destination(toAddress, Amount(1))))
      val jsonRaw = s"""
                       |{
                       |  "fromPublicKey": "${fromPublicKey.toHexString}",
                       |  "destinations": [
                       |    {
                       |      "address": "${toAddress.toBase58}",
                       |      "attoAlphAmount": "1"
                       |    }
                       |  ]
                       |}
        """.stripMargin
      checkData(transfer, jsonRaw)
    }

    {
      val transfer = BuildTransaction(
        fromPublicKey,
        AVector(Destination(toAddress, Amount(1), None, Some(TimeStamp.unsafe(1234)))),
        None,
        Some(GasBox.unsafe(1)),
        Some(GasPrice(1))
      )
      val jsonRaw = s"""
                       |{
                       |  "fromPublicKey": "${fromPublicKey.toHexString}",
                       |  "destinations": [
                       |    {
                       |      "address": "${toAddress.toBase58}",
                       |      "attoAlphAmount": "1",
                       |      "lockTime": 1234
                       |    }
                       |  ],
                       |  "gasAmount": 1,
                       |  "gasPrice": "1"
                       |}
        """.stripMargin
      checkData(transfer, jsonRaw)
    }

    {
      val tokenId1 = Hash.hash("tokenId1")

      val transfer = BuildTransaction(
        fromPublicKey,
        AVector(
          Destination(
            toAddress,
            Amount(1),
            Some(AVector(Token(tokenId1, U256.Ten))),
            Some(TimeStamp.unsafe(1234))
          )
        ),
        None,
        Some(GasBox.unsafe(1)),
        Some(GasPrice(1))
      )
      val jsonRaw = s"""
                       |{
                       |  "fromPublicKey": "${fromPublicKey.toHexString}",
                       |  "destinations": [
                       |    {
                       |      "address": "${toAddress.toBase58}",
                       |      "attoAlphAmount": "1",
                       |      "tokens": [
                       |        {
                       |          "id": "${tokenId1.toHexString}",
                       |          "amount": "10"
                       |        }
                       |      ],
                       |      "lockTime": 1234
                       |    }
                       |  ],
                       |  "gasAmount": 1,
                       |  "gasPrice": "1"
                       |}
        """.stripMargin
      checkData(transfer, jsonRaw)
    }

    {
      val tokenId1 = Hash.hash("tokenId1")

      val transfer = BuildTransaction(
        fromPublicKey,
        AVector(
          Destination(
            toAddress,
            Amount(1),
            Some(AVector(Token(tokenId1, U256.Ten))),
            Some(TimeStamp.unsafe(1234))
          )
        ),
        None,
        Some(GasBox.unsafe(1)),
        Some(GasPrice(1))
      )
      val jsonRaw = s"""
                       |{
                       |  "fromPublicKey": "${fromPublicKey.toHexString}",
                       |  "destinations": [
                       |    {
                       |      "address": "${toAddress.toBase58}",
                       |      "attoAlphAmount": "1",
                       |      "tokens": [
                       |        {
                       |          "id": "${tokenId1.toHexString}",
                       |          "amount": "10"
                       |        }
                       |      ],
                       |      "lockTime": 1234
                       |    }
                       |  ],
                       |  "gasAmount": 1,
                       |  "gasPrice": "1"
                       |}
        """.stripMargin
      checkData(transfer, jsonRaw)
    }

    {
      val tokenId1 = Hash.hash("tokenId1")
      val otxoKey1 = Hash.hash("utxo1")

      val transfer = BuildTransaction(
        fromPublicKey,
        AVector(
          Destination(
            toAddress,
            Amount(1),
            Some(AVector(Token(tokenId1, U256.Ten))),
            Some(TimeStamp.unsafe(1234))
          )
        ),
        Some(AVector(OutputRef(1, otxoKey1))),
        Some(GasBox.unsafe(1)),
        Some(GasPrice(1))
      )
      val jsonRaw = s"""
                       |{
                       |  "fromPublicKey": "${fromPublicKey.toHexString}",
                       |  "destinations": [
                       |    {
                       |      "address": "${toAddress.toBase58}",
                       |      "attoAlphAmount": "1",
                       |      "tokens": [
                       |        {
                       |          "id": "${tokenId1.toHexString}",
                       |          "amount": "10"
                       |        }
                       |      ],
                       |      "lockTime": 1234
                       |    }
                       |  ],
                       |  "utxos": [
                       |    {
                       |      "hint": 1,
                       |      "key": "${otxoKey1.toHexString}"
                       |    }
                       |  ],
                       |  "gasAmount": 1,
                       |  "gasPrice": "1"
                       |}
        """.stripMargin
      checkData(transfer, jsonRaw)
    }
  }

  it should "encode/decode BuildTransactionResult" in {
    val txId     = Hash.generate
    val gas      = GasBox.unsafe(1)
    val gasPrice = GasPrice(1)
    val result   = BuildTransactionResult("tx", gas, gasPrice, txId, 1, 2)
    val jsonRaw =
      s"""{"unsignedTx":"tx", "gasAmount": 1, "gasPrice": "1", "txId":"${txId.toHexString}", "fromGroup":1,"toGroup":2}"""
    checkData(result, jsonRaw)
  }

  it should "encode/decode SweepAddressTransaction" in {
    val txId     = Hash.generate
    val gas      = GasBox.unsafe(1)
    val gasPrice = GasPrice(1)
    val result   = SweepAddressTransaction(txId, "tx", gas, gasPrice)
    val jsonRaw =
      s"""{"txId":"${txId.toHexString}","unsignedTx":"tx", "gasAmount": 1, "gasPrice": "1"}"""
    checkData(result, jsonRaw)
  }

  it should "encode/decode SubmitTransaction" in {
    val signature = Signature.generate
    val transfer  = SubmitTransaction("tx", signature)
    val jsonRaw =
      s"""{"unsignedTx":"tx","signature":"${signature.toHexString}"}"""
    checkData(transfer, jsonRaw)
  }

  it should "encode/decode PeerStatus" in {
    val blockHash         = BlockHash.generate
    val status0: TxStatus = Confirmed(blockHash, 0, 1, 2, 3)
    val jsonRaw0 =
      s"""{"type":"Confirmed","blockHash":"${blockHash.toHexString}","txIndex":0,"chainConfirmations":1,"fromGroupConfirmations":2,"toGroupConfirmations":3}"""
    checkData(status0, jsonRaw0)

    checkData[PeerStatus](PeerStatus.Penalty(10), s"""{"type":"Penalty","value":10}""")
    checkData[PeerStatus](
      PeerStatus.Banned(TimeStamp.unsafe(1L)),
      s"""{"type":"Banned","until":1}"""
    )
  }

  it should "encode/decode TxStatus" in {
    checkData(MemPooled: TxStatus, s"""{"type":"MemPooled"}""")
    checkData(TxNotFound: TxStatus, s"""{"type":"TxNotFound"}""")
  }

  it should "encode/decode MisbehaviorAction" in {
    checkData(
      MisbehaviorAction.Ban(AVector(inetAddress)),
      s"""{"type":"Ban","peers":["127.0.0.1"]}"""
    )
    checkData(
      MisbehaviorAction.Unban(AVector(inetAddress)),
      s"""{"type":"Unban","peers":["127.0.0.1"]}"""
    )
  }

  it should "encode/decode BlockCandidate" in {
    val target = Target.Max

    val blockCandidate = BlockCandidate(
      1,
      0,
      hex"aaaa",
      target.value,
      hex"bbbbbbbbbb"
    )
    val jsonRaw =
      s"""{"fromGroup":1,"toGroup":0,"headerBlob":"aaaa","target":"${target.value}","txsBlob":"bbbbbbbbbb"}"""
    checkData(blockCandidate, jsonRaw)
  }

  it should "encode/decode BlockSolution" in {
    val blockSolution = BlockSolution(
      blockBlob = hex"bbbbbbbbbb",
      miningCount = U256.unsafe(1234)
    )
    val jsonRaw =
      s"""{"blockBlob":"bbbbbbbbbb","miningCount":"1234"}"""
    checkData(blockSolution, jsonRaw)
  }

  it should "encode/decode ApiKey" in {
    def alphaNumStrOfSizeGen(size: Int) = Gen.listOfN(size, Gen.alphaNumChar).map(_.mkString)
    val rawApiKeyGen = for {
      size      <- Gen.choose(32, 512)
      apiKeyStr <- alphaNumStrOfSizeGen(size)
    } yield apiKeyStr

    forAll(rawApiKeyGen) { rawApiKey =>
      val jsonApiKey = s""""$rawApiKey""""
      checkData(ApiKey.unsafe(rawApiKey), jsonApiKey)
    }

    val invalidRawApiKeyGen = for {
      size    <- Gen.choose(0, 31)
      invalid <- alphaNumStrOfSizeGen(size)
    } yield invalid

    forAll(invalidRawApiKeyGen) { invaildApiKey =>
      parseFail[ApiKey](
        s""""$invaildApiKey""""
      ) is s"Api key must have at least 32 characters at index 0"
    }
  }

  it should "encode/decode Compile.Script" in {
    val compile =
      Compile.Script(
        code = "0000"
      )
    val jsonRaw =
      s"""
         |{
         |  "code": "0000"
         |}
         |""".stripMargin
    checkData(compile, jsonRaw)
  }

  it should "encode/decode Compile.Contract" in {
    val compile =
      Compile.Contract(code = "0000")
    val jsonRaw =
      s"""
         |{
         |  "code": "0000"
         |}
         |""".stripMargin
    checkData(compile, jsonRaw)
  }

  it should "encode/decode BuildContract" in {
    val publicKey = PublicKey.generate
    val buildDeployContractTx = BuildDeployContractTx(
      fromPublicKey = publicKey,
      bytecode = ByteString(0, 0),
      issueTokenAmount = Some(Amount(1)),
      gasAmount = Some(GasBox.unsafe(1)),
      gasPrice = Some(GasPrice(1))
    )
    val jsonRaw =
      s"""
         |{
         |  "fromPublicKey": "${publicKey.toHexString}",
         |  "bytecode": "0000",
         |  "issueTokenAmount": "1",
         |  "gasAmount": 1,
         |  "gasPrice": "1"
         |}
         |""".stripMargin
    checkData(buildDeployContractTx, jsonRaw)
  }

  it should "encode/decode BuildDeployContractTxResult" in {
    val txId       = Hash.generate
    val contractId = Hash.generate
    val buildDeployContractTxResult = BuildDeployContractTxResult(
      fromGroup = 2,
      toGroup = 2,
      unsignedTx = "0000",
      gasAmount = GasBox.unsafe(1),
      gasPrice = GasPrice(1),
      txId = txId,
      contractAddress = Address.contract(contractId)
    )
    val jsonRaw =
      s"""
         |{
         |  "fromGroup": 2,
         |  "toGroup": 2,
         |  "unsignedTx": "0000",
         |  "gasAmount":1,
         |  "gasPrice":"1",
         |  "txId": "${txId.toHexString}",
         |  "contractAddress": "${Address.contract(contractId).toBase58}"
         |}
         |""".stripMargin
    checkData(buildDeployContractTxResult, jsonRaw)
  }

  it should "encode/decode BuildScriptTx" in {
    val publicKey = PublicKey.generate
    val buildExecuteScriptTx = BuildExecuteScriptTx(
      fromPublicKey = publicKey,
      bytecode = ByteString(0, 0),
      gasAmount = Some(GasBox.unsafe(1)),
      gasPrice = Some(GasPrice(1))
    )
    val jsonRaw =
      s"""
         |{
         |  "fromPublicKey": "${publicKey.toHexString}",
         |  "bytecode": "0000",
         |  "gasAmount": 1,
         |  "gasPrice": "1"
         |}
         |""".stripMargin
    checkData(buildExecuteScriptTx, jsonRaw)
  }

  it should "encode/decode BuildScriptTxResult" in {
    val txId = Hash.generate
    val buildExecuteScriptTxResult = BuildExecuteScriptTxResult(
      fromGroup = 1,
      toGroup = 1,
      unsignedTx = "0000",
      gasAmount = GasBox.unsafe(1),
      gasPrice = GasPrice(1),
      txId = txId
    )
    val jsonRaw =
      s"""
         |{
         |  "fromGroup": 1,
         |  "toGroup": 1,
         |  "unsignedTx": "0000",
         |  "gasAmount":1,
         |  "gasPrice":"1",
         |  "txId": "${txId.toHexString}"
         |}
         |""".stripMargin
    checkData(buildExecuteScriptTxResult, jsonRaw)
  }

  it should "encode/decode VerifySignature" in {
    val data      = Hash.generate.bytes
    val publicKey = PublicKey.generate
    val signature = Signature.generate

    val verifySignature =
      VerifySignature(data, signature, publicKey)
    val jsonRaw = s"""
                     |{
                     |  "data": "${Hex.toHexString(data)}",
                     |  "signature": "${signature.toHexString}",
                     |  "publicKey": "${publicKey.toHexString}"
                     |}
        """.stripMargin
    checkData(verifySignature, jsonRaw)
  }

  it should "encode/decode AssetState" in {
    val asset1   = AssetState(U256.unsafe(100))
    val jsonRaw1 = s"""{"attoAlphAmount": "100"}"""
    checkData(asset1, jsonRaw1)

    val asset2 = AssetState.from(U256.unsafe(100), AVector(Token(Hash.zero, U256.unsafe(123))))
    val jsonRaw2 =
      s"""
         |{
         |  "attoAlphAmount": "100",
         |  "tokens":[{"id": "0000000000000000000000000000000000000000000000000000000000000000","amount":"123"}]
         |}""".stripMargin
    checkData(asset2, jsonRaw2)
  }

  it should "encode/decode ContractState" in {
    val u256     = ValU256(U256.MaxValue)
    val i256     = ValI256(I256.MaxValue)
    val bool     = Val.True
    val byteVec  = ValByteVec(U256.MaxValue.toBytes)
    val address1 = ValAddress(generateContractAddress())
    val state = ContractState(
      generateContractAddress(),
      StatefulContract.forSMT.toContract().rightValue,
      codeHash = Hash.zero,
      initialStateHash = Some(Hash.zero),
      AVector(u256, i256, bool, byteVec, address1),
      AssetState.from(ALPH.alph(1), AVector(Token(Hash.zero, ALPH.alph(2))))
    )
    val jsonRaw =
      s"""
         |{
         |  "address": "uomjgUz6D4tLejTkQtbNJMY8apAjTm1bgQf7em1wDV7S",
         |  "bytecode": "00010700000000000118",
         |  "codeHash": "0000000000000000000000000000000000000000000000000000000000000000",
         |  "initialStateHash": "0000000000000000000000000000000000000000000000000000000000000000",
         |  "fields": [
         |    {
         |      "type": "U256",
         |      "value": "115792089237316195423570985008687907853269984665640564039457584007913129639935"
         |    },
         |    {
         |      "type": "I256",
         |      "value": "57896044618658097711785492504343953926634992332820282019728792003956564819967"
         |    },
         |    {
         |      "type": "Bool",
         |      "value": true
         |    },
         |    {
         |      "type": "ByteVec",
         |      "value": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
         |    },
         |    {
         |      "type": "Address",
         |      "value": "uomjgUz6D4tLejTkQtbNJMY8apAjTm1bgQf7em1wDV7S"
         |    }
         |  ],
         |  "asset": {
         |    "attoAlphAmount": "1000000000000000000",
         |    "tokens": [
         |      {
         |        "id": "0000000000000000000000000000000000000000000000000000000000000000",
         |        "amount": "2000000000000000000"
         |      }
         |    ]
         |  }
         |}
         |""".stripMargin
    checkData(state, jsonRaw)
  }

  it should "encode/decode CompilerResult" in new TypeSignatureFixture {
    val result0 = CompileContractResult.from(contract, contractAst)
    val jsonRaw0 =
      """
        |{
        |  "bytecode": "0701402901010707061005a000a001a003a00461b413c40de0b6b3a7640000a916011602160316041605160602",
        |  "codeHash": "eff62a4b2d4d4936a84e360c916a398d80d5000497ccd4afbd80bfe254d62096",
        |  "fields": {
        |    "signature": "TxContract Foo(aa:Bool,mut bb:U256,cc:I256,mut dd:ByteVec,ee:Address,ff:[[Bool;1];2])",
        |    "names": ["aa","bb","cc","dd","ee","ff"],
        |    "types": ["Bool", "U256", "I256", "ByteVec", "Address", "[[Bool;1];2]"]
        |  },
        |  "functions": [
        |    {
        |      "name": "bar",
        |      "signature": "@using(preapprovedAssets=true,assetsInContract=true) pub bar(a:Bool,mut b:U256,c:I256,mut d:ByteVec,e:Address,f:[[Bool;1];2])->(U256,I256,ByteVec,Address,[[Bool;1];2])",
        |      "argNames": ["a","b","c","d","e","f"],
        |      "argTypes": ["Bool", "U256", "I256", "ByteVec", "Address", "[[Bool;1];2]"],
        |      "returnTypes": ["U256", "I256", "ByteVec", "Address", "[[Bool;1];2]"]
        |    }
        |  ],
        |  "events": [
        |    {
        |      "name": "Bar",
        |      "signature": "event Bar(a:Bool,b:U256,d:ByteVec,e:Address)",
        |      "fieldNames":["a","b","d","e"],
        |      "fieldTypes": ["Bool", "U256", "ByteVec", "Address"]
        |    }
        |  ]
        |}
        |""".stripMargin
    write(result0).filter(!_.isWhitespace) is jsonRaw0.filter(!_.isWhitespace)

    val result1 = CompileScriptResult.from(script, scriptAst)
    val jsonRaw1 =
      """
        |{
        |  "bytecodeTemplate": "020103000000010201000707060716011602160316041605160602",
        |  "fields": {
        |    "signature": "TxScript Foo(aa:Bool,bb:U256,cc:I256,dd:ByteVec,ee:Address)",
        |    "names": ["aa","bb","cc","dd","ee"],
        |    "types": ["Bool", "U256", "I256", "ByteVec", "Address"]
        |  },
        |  "functions": [
        |    {
        |      "name": "main",
        |      "signature": "@using(preapprovedAssets=true) pub main()->()",
        |      "argNames": [],
        |      "argTypes": [],
        |      "returnTypes": []
        |    },
        |    {
        |      "name": "bar",
        |      "signature": "pub bar(a:Bool,mut b:U256,c:I256,mut d:ByteVec,e:Address,f:[[Bool;1];2])->(U256,I256,ByteVec,Address,[[Bool;1];2])",
        |      "argNames": ["a","b","c","d","e","f"],
        |      "argTypes": ["Bool", "U256", "I256", "ByteVec", "Address", "[[Bool;1];2]"],
        |      "returnTypes": ["U256", "I256", "ByteVec", "Address", "[[Bool;1];2]"]
        |    }
        |  ]
        |}
        |""".stripMargin
    write(result1).filter(!_.isWhitespace) is jsonRaw1.filter(!_.isWhitespace)
  }

  behavior of "TimeInterval"

  it should "validate fromTs and toTs" in {
    val ts0 = TimeStamp.unsafe(0)
    val ts1 = TimeStamp.unsafe(1)
    TimeInterval.validator(TimeInterval(ts0, ts0)).isEmpty is false
    TimeInterval.validator(TimeInterval(ts0, ts1)).isEmpty is true
    TimeInterval.validator(TimeInterval(ts1, ts0)).isEmpty is false
    TimeInterval.validator(TimeInterval(ts1, ts1)).isEmpty is false
  }

  it should "validate the time span" in {
    val timestamp = TimeStamp.now()
    val timespan  = Duration.ofHoursUnsafe(1)
    TimeInterval(timestamp, timestamp.plusMinutesUnsafe(61))
      .validateTimeSpan(timespan) is Left(
      ApiError.BadRequest(s"Time span cannot be greater than ${timespan}")
    )
    TimeInterval(timestamp, timestamp.plusMinutesUnsafe(60)).validateTimeSpan(timespan) isE ()
  }

  it should "encode/decode FixedAssetOutput" in {
    val jsonRaw =
      s"""
         |{
         |  "hint": -383063803,
         |  "key": "9f0e444c69f77a49bd0be89db92c38fe713e0963165cca12faf5712d7657120f",
         |  "attoAlphAmount": "1000000000000000000",
         |  "address": "111111111111111111111111111111111",
         |  "tokens": [],
         |  "lockTime": 0,
         |  "message": ""
         |}
         |""".stripMargin
    checkData(FixedAssetOutput.fromProtocol(assetOutput, Hash.zero, 0), jsonRaw)
  }

  it should "endcode/decode Output" in {
    val assetOutputJson =
      s"""
         |{
         |  "type": "AssetOutput",
         |  "hint": -383063803,
         |  "key": "9f0e444c69f77a49bd0be89db92c38fe713e0963165cca12faf5712d7657120f",
         |  "attoAlphAmount": "1000000000000000000",
         |  "address": "111111111111111111111111111111111",
         |  "tokens": [],
         |  "lockTime": 0,
         |  "message": ""
         |}
         |""".stripMargin
    checkData[Output](Output.from(assetOutput, Hash.zero, 0), assetOutputJson)

    val contractOutputJson =
      s"""
         |{
         |  "type": "ContractOutput",
         |  "hint": -383063804,
         |  "key": "9f0e444c69f77a49bd0be89db92c38fe713e0963165cca12faf5712d7657120f",
         |  "attoAlphAmount": "1000000000000000000",
         |  "address": "tgx7VNFoP9DJiFMFgXXtafQZkUvyEdDHT9ryamHJYrjq",
         |  "tokens": []
         |}
         |""".stripMargin
    checkData[Output](Output.from(contractOutput, Hash.zero, 0), contractOutputJson)
  }

  it should "encode/decode UnsignedTx" in {
    val unsignedTx = UnsignedTx.fromProtocol(unsignedTransaction)
    val jsonRaw =
      s"""
         |{
         |  "txId": "${unsignedTransaction.hash.toHexString}",
         |  "version": ${unsignedTransaction.version},
         |  "networkId": ${unsignedTransaction.networkId.id},
         |  "scriptOpt": ${write(unsignedTransaction.scriptOpt.map(Script.fromProtocol))},
         |  "gasAmount": ${defaultGas.value},
         |  "gasPrice": "${defaultGasPrice.value}",
         |  "inputs": ${write(unsignedTx.inputs)},
         |  "fixedOutputs": ${write(unsignedTx.fixedOutputs)}
         |}""".stripMargin

    checkData(unsignedTx, jsonRaw)
  }

  it should "encode/decode Transaction" in {
    val tx = api.Transaction.fromProtocol(transaction)
    val jsonRaw = s"""
                     |{
                     |  "unsigned": ${write(tx.unsigned)},
                     |  "scriptExecutionOk": ${tx.scriptExecutionOk},
                     |  "contractInputs": ${write(tx.contractInputs)},
                     |  "generatedOutputs": ${write(tx.generatedOutputs)},
                     |  "inputSignatures": ${write(tx.inputSignatures)},
                     |  "scriptSignatures": ${write(tx.scriptSignatures)}
                     |}""".stripMargin

    checkData(tx, jsonRaw)
  }

  it should "encode/decode TransactionTemplate" in {
    val tx = api.TransactionTemplate.fromProtocol(transactionTemplate)
    val jsonRaw = s"""
                     |{
                     |  "unsigned": ${write(tx.unsigned)},
                     |  "inputSignatures": ${write(tx.inputSignatures)},
                     |  "scriptSignatures": ${write(tx.scriptSignatures)}
                     |}""".stripMargin

    checkData(tx, jsonRaw)
  }
}
