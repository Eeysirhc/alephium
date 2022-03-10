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

package org.alephium.api.model

import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, ContractId, ContractOutput}
import org.alephium.protocol.vm.{LockupScript, StatefulContract}
import org.alephium.util.{AVector, U256}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class ContractState(
    address: Address.Contract,
    bytecode: StatefulContract,
    codeHash: Hash,
    fields: AVector[Val] = AVector.empty,
    asset: ContractState.Asset
) {
  def id: ContractId = address.lockupScript.contractId
}

object ContractState {
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  final case class Asset(alphAmount: U256, tokens: AVector[Token] = AVector.empty) {
    def toContractOutput(contractId: ContractId): ContractOutput = {
      ContractOutput(
        alphAmount,
        LockupScript.p2c(contractId),
        tokens.map(token => (token.id, token.amount))
      )
    }
  }

  object Asset {
    def from(output: ContractOutput): Asset = {
      ContractState.Asset(output.amount, output.tokens.map(pair => Token(pair._1, pair._2)))
    }
  }
}