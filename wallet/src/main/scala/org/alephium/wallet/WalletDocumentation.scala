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

package org.alephium.wallet

import sttp.tapir.Endpoint
import sttp.tapir.docs.openapi._
import sttp.tapir.openapi.OpenAPI

import org.alephium.wallet.api.WalletEndpoints

trait WalletDocumentation extends WalletEndpoints {

  val walletEndpoints: List[Endpoint[_, _, _, _]] = List(
    createWallet,
    restoreWallet,
    listWallets,
    lockWallet,
    unlockWallet,
    deleteWallet,
    getBalances,
    transfer,
    getAddresses,
    getMinerAddresses,
    deriveNextAddress,
    deriveNextMinerAddresses,
    changeActiveAddress
  )

  lazy val walletOpenAPI: OpenAPI = walletEndpoints.toOpenAPI("Alephium Wallet", "1.0")
}