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

package org.alephium.protocol.model

import org.scalatest.Assertion

import org.alephium.util.{AlephiumSpec, Hex}

class TargetSpec extends AlephiumSpec {
  it should "convert between big integer and compact bits" in {
    def check(bits: String): Assertion = {
      checkGeneric(bits, bits)
    }

    def checkGeneric(bits: String, expected: String): Assertion = {
      val bytes = Hex.from(bits).get
      Target.unsafe(Target.unsafe(bytes).value).bits is Hex.from(expected).get
    }

    check("00000000")
    check("03123456")
    check("04123456")
    check("12345667")
    check("20FFFFFF")

    checkGeneric("00123456", "00000000")
    checkGeneric("01123456", "01120000")
    checkGeneric("02123456", "02123400")
  }
}