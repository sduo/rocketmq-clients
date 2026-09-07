/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { strict as assert } from 'node:assert';
import {
  createDuration,
  getTimestamp,
  calculateStringSipHash24,
} from '../../src/util';

describe('test/util/index.test.ts', () => {
  describe('getTimestamp()', () => {
    it('should work', () => {
      const timestamp = getTimestamp();
      assert(timestamp.seconds);
      assert(timestamp.nanos);
    });
  });

  describe('createDuration()', () => {
    it('should split whole seconds correctly', () => {
      const duration = createDuration(2000);
      assert.equal(duration.getSeconds(), 2);
      assert.equal(duration.getNanos(), 0);
      assert(duration.serializeBinary().length > 0);
    });

    it('should split non-whole-second milliseconds correctly', () => {
      const duration = createDuration(2173);
      assert.equal(duration.getSeconds(), 2);
      assert.equal(duration.getNanos(), 173000000);
      // protobuf Duration.seconds is int64, serialization must not throw
      assert(duration.serializeBinary().length > 0);
    });
  });

  describe('calculateStringSipHash24()', () => {
    it('should work', () => {
      assert.equal(calculateStringSipHash24('foo哈哈😄2222哈哈'), 11716758754047899126n);
      assert.equal(calculateStringSipHash24('foo哈哈😄2222哈哈') % 3n, 2n);
    });
  });
});
