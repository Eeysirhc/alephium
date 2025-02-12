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

package org.alephium.protocol.vm

import scala.collection.mutable

import org.alephium.io._

final class CachedLogStates(
    val underlying: KeyValueStorage[LogStatesId, LogStates],
    val caches: mutable.LinkedHashMap[LogStatesId, Cache[LogStates]]
) extends CachedKV[LogStatesId, LogStates, Cache[LogStates]] {
  protected def getOptFromUnderlying(key: LogStatesId): IOResult[Option[LogStates]] = {
    CachedKV.getOptFromUnderlying(underlying, caches, key)
  }

  def persist(): IOResult[KeyValueStorage[LogStatesId, LogStates]] = {
    CachedKV.persist(underlying, caches)
  }

  def staging(): StagingLogStates = new StagingLogStates(this, mutable.LinkedHashMap.empty)
}

object CachedLogStates {
  def from(storage: KeyValueStorage[LogStatesId, LogStates]): CachedLogStates = {
    new CachedLogStates(storage, mutable.LinkedHashMap.empty)
  }
}
