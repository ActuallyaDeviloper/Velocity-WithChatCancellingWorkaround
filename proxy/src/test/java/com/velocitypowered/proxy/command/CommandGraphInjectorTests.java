/*
 * Copyright (C) 2018 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.command;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CommandGraphInjectorTests {

  private static final CommandSource SOURCE = MockCommandSource.INSTANCE;

  private VelocityCommandManager manager;
  private RootCommandNode<CommandSource> dest;

  @BeforeEach
  void setUp() {
    this.manager = new VelocityCommandManager(OldCommandManagerTests.EVENT_MANAGER);
    this.dest = new RootCommandNode<>();
  }

  @Test
  void testInjectInvocableCommand() {
    final var meta = manager.metaBuilder("hello").build();
    manager.register(meta, (SimpleCommand) invocation -> fail());
    manager.getInjector().inject(dest, SOURCE);

    // Preserves alias and arguments node
    final var expected = manager.getDispatcher().getRoot();
    assertEquals(expected, dest);
  }

  @Test
  void testFiltersImpermissibleAlias() {
    final var callCount = new AtomicInteger();

    final var meta = manager.metaBuilder("hello").build();
    manager.register(meta, new SimpleCommand() {
      @Override
      public void execute(final Invocation invocation) {
        fail();
      }

      @Override
      public boolean hasPermission(final Invocation invocation) {
        assertEquals(SOURCE, invocation.source());
        assertEquals("hello", invocation.alias());
        assertArrayEquals(new String[0], invocation.arguments());
        callCount.incrementAndGet();
        return false;
      }
    });
    manager.getInjector().inject(dest, SOURCE);

    assertTrue(dest.getChildren().isEmpty());
    assertEquals(1, callCount.get());
  }

  @Test
  void testInjectsBrigadierCommand() {
    final LiteralCommandNode<CommandSource> node = LiteralArgumentBuilder
            .<CommandSource>literal("hello")
            .then(literal("world"))
            .then(argument("count", integer()))
            .build();
    manager.register(new BrigadierCommand(node));
    manager.getInjector().inject(dest, SOURCE);

    assertEquals(node, dest.getChild("hello"));
  }

  @Test
  void testFiltersImpermissibleBrigadierCommandChildren() {
    final var callCount = new AtomicInteger();

    final var registered = LiteralArgumentBuilder
            .<CommandSource>literal("greet")
            .then(LiteralArgumentBuilder
                    .<CommandSource>literal("somebody")
                    .requires(source -> {
                      callCount.incrementAndGet();
                      return false;
                    }))
            .build();
    manager.register(new BrigadierCommand(registered));
    manager.getInjector().inject(dest, SOURCE);

    final var expected = LiteralArgumentBuilder
            .literal("greet")
            .build();
    assertEquals(expected, dest.getChild("greet"));
    assertEquals(1, callCount.get());
  }

  @Test
  void testBrigadierCommandAliasRedirectsNotAllowed() {
    final var registered = LiteralArgumentBuilder
            .<CommandSource>literal("origin")
            .redirect(LiteralArgumentBuilder
                    .<CommandSource>literal("target")
                    .build())
            .build();
    manager.register(new BrigadierCommand(registered));
    manager.getInjector().inject(dest, SOURCE);

    final var expected = LiteralArgumentBuilder
            .<CommandSource>literal("origin")
            .build();
    assertEquals(expected, dest.getChild("origin"));
  }

  @Test
  void testFiltersImpermissibleBrigadierCommandRedirects() {
    final var callCount = new AtomicInteger();

    final var registered = LiteralArgumentBuilder
            .<CommandSource>literal("hello")
            .then(LiteralArgumentBuilder
                .<CommandSource>literal("origin")
                .redirect(LiteralArgumentBuilder
                    .<CommandSource>literal("target")
                    .requires(source -> {
                      callCount.incrementAndGet();
                      return false;
                    })
                    .build()
                )
            )
            .build();
    manager.register(new BrigadierCommand(registered));
    manager.getInjector().inject(dest, SOURCE);

    final var expected = LiteralArgumentBuilder
            .<CommandSource>literal("hello")
            .then(literal("origin"))
            .build();
    assertEquals(expected, dest.getChild("hello"));
    assertEquals(1, callCount.get());
  }

  @Test
  void testInjectOverridesAliasInDestination() {
    final var registered = LiteralArgumentBuilder
            .<CommandSource>literal("foo")
            .then(literal("bar"))
            .build();
    manager.register(new BrigadierCommand(registered));

    final var original = LiteralArgumentBuilder
            .<CommandSource>literal("foo")
            .then(literal("baz"))
            .build();
    dest.addChild(original);
    manager.getInjector().inject(dest, SOURCE);

    assertEquals(registered, dest.getChild("foo"));
  }
}
