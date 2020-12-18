/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans;

import java.util.Map;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;

/**
 * 可以访问命名属性（例如，对象的Bean属性或对象中的字段）的类的通用接口用作BeanWrapper基本接口。
 *
 * @author Juergen Hoeller
 * @since 1.1
 * @see BeanWrapper
 * @see PropertyAccessorFactory#forBeanPropertyAccess
 * @see PropertyAccessorFactory#forDirectFieldAccess
 */
public interface PropertyAccessor {

	/**
	 * 嵌套属性的路径分隔符
	 * 例如："foo.bar"调用的方法就是getFoo().getBar()
	 */
	String NESTED_PROPERTY_SEPARATOR = ".";
	char NESTED_PROPERTY_SEPARATOR_CHAR = '.';

	/**
	 * 用于指示下标index的符号
	 * 例如：person.addresses[0]中 "0"被符号标记，表示 "0"是一个下标index
	 */
	String PROPERTY_KEY_PREFIX = "[";
	char PROPERTY_KEY_PREFIX_CHAR = '[';
	String PROPERTY_KEY_SUFFIX = "]";
	char PROPERTY_KEY_SUFFIX_CHAR = ']';


	/**
	 * 判断属性是否可读。如果属性不存在，则返回false
	 *
	 * @param propertyName 要检查的属性
	 * (may be a nested path and/or an indexed/mapped property)【待验证】
	 * @return 该属性是否可读
	 */
	boolean isReadableProperty(String propertyName);

	/**
	 * 判断属性是否可写。如果属性不存在，则返回false
	 *
	 * @param propertyName 要检查的属性
	 * (may be a nested path and/or an indexed/mapped property)【待验证】
	 * @return 该属性是否可写
	 */
	boolean isWritableProperty(String propertyName);

	/**
	 * 获取属性的属性类型
	 *
	 * @param propertyName 要检查的属性（可以是嵌套路径/索引/映射的属性）
	 * @return 属性类型，不确定则返回null
	 * @throws PropertyAccessException 属性有效但是访问失败
	 */
	@Nullable
	Class<?> getPropertyType(String propertyName) throws BeansException;

	/**
	 * 获取属性的类型描述符：最好从read方法返回到write方法。
	 *
	 * @param propertyName 要检查的属性（可以是嵌套路径/索引/映射的属性）
	 * @return 属性的类型描述符；如果不确定，则为null
	 * @throws PropertyAccessException 属性有效，但访问器方法失败
	 */
	@Nullable
	TypeDescriptor getPropertyTypeDescriptor(String propertyName) throws BeansException;

	/**
	 * 获取属性的值
	 *
	 * @param propertyName 要获取的属性名称（可以是嵌套路径/索引/映射的属性）
	 * @return 属性的值
	 * @throws InvalidPropertyException 指定属性不存在或不可读
	 * @throws PropertyAccessException 属性有效，但访问器方法失败
	 */
	@Nullable
	Object getPropertyValue(String propertyName) throws BeansException;

	/**
	 * 属性赋值
	 *
	 * @param propertyName 要赋值的属性名称（可以是嵌套路径/索引/映射的属性）
	 * @param value 新的属性值
	 * @throws InvalidPropertyException 指定属性不存在或不可写
	 * @throws PropertyAccessException 属性有效，但访问器方法失败
	 */
	void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException;

	/**
	 * 通过PropertyValue为属性赋值
	 *
	 * @param pv 包含新属性值的对象
	 * @throws InvalidPropertyException 指定属性不存在或不可写
	 * @throws PropertyAccessException 属性有效，但访问器方法失败
	 */
	void setPropertyValue(PropertyValue pv) throws BeansException;

	/**
	 * 通过Map批量赋值
	 *
	 * @param map 包含新属性值的Map
	 * @throws InvalidPropertyException 指定属性不存在或不可写
	 * @throws PropertyBatchUpdateException 更新过程中如果有属性抛出PropertyAccessException异常不会影响其他属性更新，
	 * 会最终抛出一个包含所有异常的PropertyBatchUpdateException异常，其他属性正常更新
	 */
	void setPropertyValues(Map<?, ?> map) throws BeansException;

	/**
	 * 通过PropertyValues批量赋值(不允许未知字段或无效字段)
	 *
	 * @param map 包含新属性值的PropertyValues
	 * @throws InvalidPropertyException 指定属性不存在或不可写
	 * @throws PropertyBatchUpdateException 更新过程中如果有属性抛出PropertyAccessException异常不会影响其他属性更新，
	 * 会最终抛出一个包含所有异常的PropertyBatchUpdateException异常，其他属性正常更新
	 */
	void setPropertyValues(PropertyValues pvs) throws BeansException;
	/**
	 * 通过PropertyValues批量赋值(忽略未知字段)
	 */
	void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown)
			throws BeansException;
	/**
	 * 通过PropertyValues批量赋值(忽略无效字段)
	 */
	void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid)
			throws BeansException;

}
