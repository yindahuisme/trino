/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.parquet;

import io.trino.spi.block.Block;
import io.trino.spi.block.SqlMap;
import io.trino.spi.block.SqlRow;
import io.trino.spi.function.InvocationConvention;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.MapType;
import io.trino.spi.type.RowType;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeOperators;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.trino.spi.function.InvocationConvention.InvocationArgumentConvention.BLOCK_POSITION_NOT_NULL;
import static io.trino.spi.function.InvocationConvention.InvocationReturnConvention.FAIL_ON_NULL;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.Objects.requireNonNull;

/**
 * Based on io.trino.rcfile.ValidationHash and io.trino.orc.ValidationHash
 * with minor differences in handling of timestamp and map types.
 */
class ValidationHash
{
    // This value is a large arbitrary prime
    private static final long NULL_HASH_CODE = 0x6e3efbd56c16a0cbL;

    private static final MethodHandle MAP_HASH;
    private static final MethodHandle ARRAY_HASH;
    private static final MethodHandle ROW_HASH;

    static {
        try {
            MAP_HASH = lookup().findStatic(
                    ValidationHash.class,
                    "mapHash",
                    MethodType.methodType(long.class, MapType.class, ValidationHash.class, ValidationHash.class, Block.class, int.class));
            ARRAY_HASH = lookup().findStatic(
                    ValidationHash.class,
                    "arrayHash",
                    MethodType.methodType(long.class, ArrayType.class, ValidationHash.class, Block.class, int.class));
            ROW_HASH = lookup().findStatic(
                    ValidationHash.class,
                    "rowHash",
                    MethodType.methodType(long.class, RowType.class, ValidationHash[].class, Block.class, int.class));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // This should really come from the environment, but there is no good way to get a value here
    private static final TypeOperators VALIDATION_TYPE_OPERATORS_CACHE = new TypeOperators();

    public static ValidationHash createValidationHash(Type type)
    {
        requireNonNull(type, "type is null");
        if (type instanceof MapType mapType) {
            ValidationHash keyHash = createValidationHash(mapType.getKeyType());
            ValidationHash valueHash = createValidationHash(mapType.getValueType());
            return new ValidationHash(MAP_HASH.bindTo(mapType).bindTo(keyHash).bindTo(valueHash));
        }

        if (type instanceof ArrayType arrayType) {
            ValidationHash elementHash = createValidationHash(arrayType.getElementType());
            return new ValidationHash(ARRAY_HASH.bindTo(arrayType).bindTo(elementHash));
        }

        if (type instanceof RowType rowType) {
            ValidationHash[] fieldHashes = rowType.getTypeParameters().stream()
                    .map(ValidationHash::createValidationHash)
                    .toArray(ValidationHash[]::new);
            return new ValidationHash(ROW_HASH.bindTo(rowType).bindTo(fieldHashes));
        }

        return new ValidationHash(VALIDATION_TYPE_OPERATORS_CACHE.getHashCodeOperator(type, InvocationConvention.simpleConvention(FAIL_ON_NULL, BLOCK_POSITION_NOT_NULL)));
    }

    private final MethodHandle hashCodeOperator;

    private ValidationHash(MethodHandle hashCodeOperator)
    {
        this.hashCodeOperator = requireNonNull(hashCodeOperator, "hashCodeOperator is null");
    }

    public long hash(Block block, int position)
    {
        if (block.isNull(position)) {
            return NULL_HASH_CODE;
        }
        try {
            return (long) hashCodeOperator.invokeExact(block, position);
        }
        catch (Throwable throwable) {
            throwIfUnchecked(throwable);
            throw new RuntimeException(throwable);
        }
    }

    private static long mapHash(MapType type, ValidationHash keyHash, ValidationHash valueHash, Block block, int position)
    {
        SqlMap sqlMap = type.getObject(block, position);
        int rawOffset = sqlMap.getRawOffset();
        Block rawKeyBlock = sqlMap.getRawKeyBlock();
        Block rawValueBlock = sqlMap.getRawValueBlock();

        long hash = 0;
        for (int i = 0; i < sqlMap.getSize(); i++) {
            hash = 31 * hash + keyHash.hash(rawKeyBlock, rawOffset + i);
            hash = 31 * hash + valueHash.hash(rawValueBlock, rawOffset + i);
        }
        return hash;
    }

    private static long arrayHash(ArrayType type, ValidationHash elementHash, Block block, int position)
    {
        Block array = type.getObject(block, position);
        long hash = 0;
        for (int i = 0; i < array.getPositionCount(); i++) {
            hash = 31 * hash + elementHash.hash(array, i);
        }
        return hash;
    }

    private static long rowHash(RowType type, ValidationHash[] fieldHashes, Block block, int position)
    {
        SqlRow row = type.getObject(block, position);
        int rawIndex = row.getRawIndex();
        long hash = 0;
        for (int i = 0; i < row.getFieldCount(); i++) {
            hash = 31 * hash + fieldHashes[i].hash(row.getRawFieldBlock(i), rawIndex);
        }
        return hash;
    }
}
