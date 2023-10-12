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
package io.trino.sql.relational;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.trino.spi.type.Type;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CallExpression.class, name = "call"),
        @JsonSubTypes.Type(value = ConstantExpression.class, name = "constant"),
        @JsonSubTypes.Type(value = InputReferenceExpression.class, name = "input"),
        @JsonSubTypes.Type(value = LambdaDefinitionExpression.class, name = "lambda"),
        @JsonSubTypes.Type(value = SpecialForm.class, name = "special"),
        @JsonSubTypes.Type(value = VariableReferenceExpression.class, name = "variable"),
})
public abstract sealed class RowExpression
        permits CallExpression, ConstantExpression, InputReferenceExpression, LambdaDefinitionExpression, SpecialForm, VariableReferenceExpression
{
    public abstract Type getType();

    @Override
    public abstract boolean equals(Object other);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();

    public abstract <R, C> R accept(RowExpressionVisitor<R, C> visitor, C context);
}
