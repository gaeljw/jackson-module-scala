package com.fasterxml.jackson.module.scala.deser

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.`type`.MapLikeType
import com.fasterxml.jackson.databind.deser.{ContextualDeserializer, Deserializers, ValueInstantiator}
import com.fasterxml.jackson.databind.deser.std.{ContainerDeserializerBase, MapDeserializer, StdValueInstantiator}
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer

import scala.collection.{Map, mutable}

abstract class GenericMapFactoryDeserializerResolver[CC[K, V], CF[X[_, _]]] extends Deserializers.Base {
  type Collection[K, V] = CC[K, V]
  type Factory = CF[CC]
  type Builder[K, V] = mutable.Builder[(K, V), _ <: Collection[K, V]]

  // Subclasses need to implement the following:
  val CLASS_DOMAIN: Class[_]
  val factories: List[(Class[_], Factory)]
  def builderFor[K, V](factory: Factory, keyType: JavaType, valueType: JavaType): Builder[K, V]

  def builderFor[K, V](cls: Class[_], keyType: JavaType, valueType: JavaType): Builder[K, V] = factories
    .find(_._1.isAssignableFrom(cls))
    .map(_._2)
    .map(builderFor[K, V](_, keyType, valueType))
    .getOrElse(throw new IllegalStateException(s"Could not find deserializer for ${cls.getCanonicalName}. File issue on github:fasterxml/jackson-scala-module."))

  override def findMapLikeDeserializer(theType: MapLikeType,
                                       config: DeserializationConfig,
                                       beanDesc: BeanDescription,
                                       keyDeserializer: KeyDeserializer,
                                       elementTypeDeserializer: TypeDeserializer,
                                       elementDeserializer: JsonDeserializer[_]): JsonDeserializer[_] = {
    if (!CLASS_DOMAIN.isAssignableFrom(theType.getRawClass)) None.orNull
    else {
      val instantiator = new Instantiator(config, theType)
      new Deserializer(theType, instantiator, keyDeserializer, elementDeserializer, elementTypeDeserializer)
    }
  }

  private class BuilderWrapper[K, V >: AnyRef](val builder: Builder[K, V]) extends java.util.AbstractMap[K, V] {
    private var baseMap: Map[Any, V] = Map.empty

    override def put(k: K, v: V): V = { builder += ((k, v)); v }

    // Used by the deserializer when using readerForUpdating
    override def get(key: Any): V = baseMap.get(key).orNull

    // Isn't used by the deserializer
    override def entrySet(): java.util.Set[java.util.Map.Entry[K, V]] = throw new UnsupportedOperationException

    def setInitialValue(init: Collection[K, V]): Unit = {
      init.asInstanceOf[Map[K, V]].foreach(Function.tupled(put))
      baseMap = init.asInstanceOf[Map[Any, V]]
    }
  }

  private class Instantiator(config: DeserializationConfig, mapType: MapLikeType) extends StdValueInstantiator(config, mapType) {
    override def canCreateUsingDefault = true

    override def createUsingDefault(ctxt: DeserializationContext) =
      new BuilderWrapper[AnyRef, AnyRef](builderFor[AnyRef, AnyRef](mapType.getRawClass, mapType.getKeyType, mapType.getContentType))
  }

  private class Deserializer[K, V](mapType: MapLikeType, containerDeserializer: MapDeserializer)
    extends ContainerDeserializerBase[CC[K, V]](mapType) with ContextualDeserializer {

    def this(mapType: MapLikeType, valueInstantiator: ValueInstantiator, keyDeser: KeyDeserializer, valueDeser: JsonDeserializer[_], valueTypeDeser: TypeDeserializer) = {
      this(mapType, new MapDeserializer(mapType, valueInstantiator, keyDeser, valueDeser.asInstanceOf[JsonDeserializer[AnyRef]], valueTypeDeser))
    }

    override def getContentType: JavaType = containerDeserializer.getContentType

    override def getContentDeserializer: JsonDeserializer[AnyRef] = containerDeserializer.getContentDeserializer

    override def createContextual(ctxt: DeserializationContext, property: BeanProperty): JsonDeserializer[_] = {
      val newDelegate = containerDeserializer.createContextual(ctxt, property).asInstanceOf[MapDeserializer]
      new Deserializer(mapType, newDelegate)
    }

    override def deserialize(jp: JsonParser, ctxt: DeserializationContext): CC[K, V] = {
      containerDeserializer.deserialize(jp, ctxt) match {
        case wrapper: BuilderWrapper[_, _] => wrapper.builder.result().asInstanceOf[CC[K, V]]
      }
    }

    override def deserialize(jp: JsonParser, ctxt: DeserializationContext, intoValue: CC[K, V]): CC[K, V] = {
      val bw = newBuilderWrapper(ctxt)
      bw.setInitialValue(intoValue.asInstanceOf[CC[AnyRef, AnyRef]])
      containerDeserializer.deserialize(jp, ctxt, bw) match {
        case wrapper: BuilderWrapper[_, _] => wrapper.builder.result().asInstanceOf[CC[K, V]]
      }
    }

    override def getEmptyValue(ctxt: DeserializationContext): Object = {
      val bw = newBuilderWrapper(ctxt)
      bw.builder.result().asInstanceOf[Object]
    }

    private def newBuilderWrapper(ctxt: DeserializationContext): BuilderWrapper[AnyRef, AnyRef] = {
      containerDeserializer.getValueInstantiator.createUsingDefault(ctxt).asInstanceOf[BuilderWrapper[AnyRef, AnyRef]]
    }
  }
}
