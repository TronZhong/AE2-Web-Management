package com.ae2webmanagement.backend.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import com.ae2webmanagement.backend.client.ModApiClient;
import com.ae2webmanagement.backend.client.ModApiException;
import com.ae2webmanagement.backend.domain.Account;
import com.ae2webmanagement.backend.dto.ModAccountEntry;
import com.ae2webmanagement.backend.dto.ModAccountListResponse;
import com.ae2webmanagement.backend.repository.AccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AccountSyncService} 单元测试（roadmap §11 账号同步）：
 * 按 playerUuid upsert——新账号建档、已有账号跟随玩家名与密码哈希、
 * 用户名冲突跳过、缺 playerUuid 跳过、模组不可达静默跳过。
 */
@ExtendWith(MockitoExtension.class)
class AccountSyncServiceTests {

	private static final String PLAYER_UUID = "9b2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a099";

	private static final String OTHER_UUID = "1a2ad6c1-4b7a-4c1e-9a3f-2f8f6f41a088";

	@Mock
	private ModApiClient modApiClient;

	@Mock
	private AccountRepository accountRepository;

	@InjectMocks
	private AccountSyncService service;

	/** 新账号：以玩家名为用户名建档，createdAt 取模组注册时间，哈希原样入库。 */
	@Test
	void newAccountIsCreatedFromModEntry() {
		ModAccountEntry entry = new ModAccountEntry(PLAYER_UUID, "Steve", "pbkdf2:60000:c2FsdA==:aGFzaA==", 1760000000000L);
		when(this.modApiClient.listAccounts()).thenReturn(new ModAccountListResponse(List.of(entry)));
		when(this.accountRepository.findByUsername("Steve")).thenReturn(Optional.empty());
		when(this.accountRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.empty());

		this.service.sync();

		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(this.accountRepository).save(captor.capture());
		Account saved = captor.getValue();
		assertThat(saved.getUsername()).isEqualTo("Steve");
		assertThat(saved.getPlayerUuid()).isEqualTo(PLAYER_UUID);
		assertThat(saved.getPasswordHash()).isEqualTo("pbkdf2:60000:c2FsdA==:aGFzaA==");
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	/** 已有账号：玩家名与密码哈希跟随模组更新（玩家改名 / 游戏内改密后网页跟随）。 */
	@Test
	void existingAccountFollowsPlayerNameAndPasswordHash() {
		ModAccountEntry entry = new ModAccountEntry(PLAYER_UUID, "Alex", "pbkdf2:60000:bmV3c2FsdA==:bmV3aGFzaA==", 1760000000000L);
		when(this.modApiClient.listAccounts()).thenReturn(new ModAccountListResponse(List.of(entry)));
		when(this.accountRepository.findByUsername("Alex")).thenReturn(Optional.empty());
		Account existing = new Account();
		existing.setUsername("Steve");
		existing.setPlayerUuid(PLAYER_UUID);
		existing.setPasswordHash("pbkdf2:60000:c2FsdA==:aGFzaA==");
		existing.setCreatedAt(java.time.Instant.ofEpochMilli(1750000000000L));
		when(this.accountRepository.findByPlayerUuid(PLAYER_UUID)).thenReturn(Optional.of(existing));

		this.service.sync();

		ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
		verify(this.accountRepository).save(captor.capture());
		Account saved = captor.getValue();
		assertThat(saved.getUsername()).isEqualTo("Alex");
		assertThat(saved.getPasswordHash()).isEqualTo("pbkdf2:60000:bmV3c2FsdA==:bmV3aGFzaA==");
		// 已有账号保留原建档时间
		assertThat(saved.getCreatedAt()).isEqualTo(java.time.Instant.ofEpochMilli(1750000000000L));
	}

	/** 用户名冲突：玩家名与其他账号的用户名冲突 → 跳过该条，不写库。 */
	@Test
	void playerNameConflictSkipsEntry() {
		ModAccountEntry entry = new ModAccountEntry(PLAYER_UUID, "Steve", "pbkdf2:60000:c2FsdA==:aGFzaA==", 1L);
		when(this.modApiClient.listAccounts()).thenReturn(new ModAccountListResponse(List.of(entry)));
		Account other = new Account();
		other.setUsername("Steve");
		other.setPlayerUuid(OTHER_UUID);
		other.setPasswordHash("x");
		other.setCreatedAt(java.time.Instant.now());
		when(this.accountRepository.findByUsername("Steve")).thenReturn(Optional.of(other));

		this.service.sync();

		verify(this.accountRepository, never()).save(any(Account.class));
	}

	/** 缺 playerUuid 的记录（防御）→ 跳过。 */
	@Test
	void entryWithoutPlayerUuidSkipped() {
		ModAccountEntry entry = new ModAccountEntry(null, "Steve", "pbkdf2:60000:c2FsdA==:aGFzaA==", 1L);
		when(this.modApiClient.listAccounts()).thenReturn(new ModAccountListResponse(List.of(entry)));

		this.service.sync();

		verify(this.accountRepository, never()).save(any(Account.class));
	}

	/** 模组不可达 → 静默跳过本轮，不写库。 */
	@Test
	void modUnreachableSkipsSilently() {
		when(this.modApiClient.listAccounts()).thenThrow(new ResourceAccessException("connect timed out"));

		this.service.sync();

		verify(this.accountRepository, never()).save(any(Account.class));
	}

	/** 旧版模组无 /accounts 端点（404 not_found）→ 静默跳过本轮（向后兼容）。 */
	@Test
	void legacyModWithoutAccountsEndpointSkipsSilently() {
		when(this.modApiClient.listAccounts()).thenThrow(new ModApiException(404, "not_found"));

		this.service.sync();

		verify(this.accountRepository, never()).save(any(Account.class));
	}
}
